package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.projectservice.client.IaServiceClient;
import tn.rihab.projectservice.dto.ChampResult;
import tn.rihab.projectservice.dto.ExtractionRequestDto;
import tn.rihab.projectservice.dto.ExtractionResponseDto;
import tn.rihab.projectservice.model.entity.ExtractionMetadata;
import tn.rihab.projectservice.repository.ExtractionMetadataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionService {

    private final IaServiceClient iaServiceClient;
    private final ExtractionMetadataRepository metadataRepository;
    private final AuditTrailService auditTrailService;
    private final tn.rihab.projectservice.repository.AiLogRepository aiLogRepository;

    @Value("${scoring.tjm.min:350}")  private Double tjmMin;
    @Value("${scoring.tjm.max:3000}") private Double tjmMax;

    @Transactional
    public ExtractionResponseDto extractPhase1(UUID dossierId, String documentText) {
        log.info("[Extraction P1] Dossier {} — appel ia-service", dossierId);

        ExtractionRequestDto request = ExtractionRequestDto.builder()
                .dossierId(dossierId)
                .documentText(documentText)
                .phase("P1")
                .build();

        ExtractionResponseDto response = iaServiceClient.extractPhase1(request);

        if (response.getTokenUsage() != null) {
            aiLogRepository.save(tn.rihab.projectservice.model.entity.AiLog.builder()
                    .dossierId(dossierId)
                    .actionType("Extraction Phase 1")
                    .modelUsed("Claude 3.5 Sonnet")
                    .tokensConsumed(response.getTokenUsage())
                    .inputTokens(response.getInputTokens())
                    .outputTokens(response.getOutputTokens())
                    .estimatedCost(response.getEstimatedCost())
                    .responseTimeMs(response.getProcessingTimeMs())
                    .status("SUCCESS")
                    .build());
        }

        persistMetadata(dossierId, response.getChamps(), "P1");

        Double tjm = calculerTjm(response.getChamps());
        if (tjm != null) {
            response.setTjmImplicite(tjm);
            response.setTjmHorsPlage(tjm < tjmMin || tjm > tjmMax);
        }

        List<String> alertes = new ArrayList<>(response.getAlertes() != null ? response.getAlertes() : List.of());
        List<String> bloquantes = new ArrayList<>(response.getAlertesBloquantes() != null ? response.getAlertesBloquantes() : List.of());

        alertesCroisees(response.getChamps(), tjm, alertes, bloquantes);

        response.setAlertes(alertes);
        response.setAlertesBloquantes(bloquantes);

        auditTrailService.log(dossierId, "PARSING_COMPLETED", "claude-api",
                String.format("{\"champsExtraits\":%d,\"alertes\":%d,\"tjm\":%.0f}",
                        response.getChamps().size(), alertes.size(), (tjm != null ? tjm : 0.0)), null);

        return response;
    }

    @Transactional
    public ChampResult reextractField(UUID dossierId, String fieldName, String documentText) {
        ExtractionMetadata meta = metadataRepository.findByDossierIdAndFieldName(dossierId, fieldName)
                .orElseThrow(() -> new IllegalStateException("Champ non trouvé : " + fieldName));

        if (meta.getReextractionCount() >= 2) {
            throw new IllegalStateException("Max 2 ré-extractions atteint pour " + fieldName);
        }

        ExtractionRequestDto request = ExtractionRequestDto.builder()
                .dossierId(dossierId).documentText(documentText).phase("REFIELD_" + fieldName).fieldName(fieldName).build();

        ExtractionResponseDto response = iaServiceClient.reextractField(request);
        ChampResult result = response.getChamps().get(fieldName);

        if (response.getTokenUsage() != null) {
            aiLogRepository.save(tn.rihab.projectservice.model.entity.AiLog.builder()
                    .dossierId(dossierId)
                    .actionType("Ré-extraction P1: " + fieldName)
                    .modelUsed("Claude 3.5 Sonnet")
                    .tokensConsumed(response.getTokenUsage())
                    .estimatedCost(response.getEstimatedCost())
                    .responseTimeMs(response.getProcessingTimeMs())
                    .status("SUCCESS")
                    .build());
        }

        if (result != null) {
            String val = String.valueOf(result.getValeur());
            meta.setValeurClaude(val);
            meta.setValeurFinale(val);
            meta.setConfiance(result.getConfiance());
            meta.setSourceExtrait(result.getSource());
            meta.setReextractionCount(meta.getReextractionCount() + 1);
            metadataRepository.save(meta);
        }
        return result;
    }

    public List<ExtractionMetadata> getExtractionP1(UUID dossierId) {
        return metadataRepository.findByDossierIdAndPhase(dossierId, "P1");
    }

    private void persistMetadata(UUID dossierId, Map<String, ChampResult> champs, String phase) {
        champs.forEach((fieldName, result) -> {
            // --- NOUVEAU : On ignore la date limite extraite par l'IA ---
            if ("DT_LIM_SOUM".equals(fieldName)) {
                return;
            }

            String val = String.valueOf(result.getValeur());
            ExtractionMetadata meta = metadataRepository.findByDossierIdAndFieldName(dossierId, fieldName)
                    .orElseGet(() -> ExtractionMetadata.builder()
                            .dossierId(dossierId)
                            .fieldName(fieldName)
                            .build());

            meta.setValeurClaude(val);
            meta.setValeurFinale(val);
            meta.setConfiance(result.getConfiance());
            meta.setPhase(phase);
            meta.setSource("claude_extraction");
            meta.setSourceExtrait(result.getSource());

            metadataRepository.save(meta);
        });
    }

    private Double calculerTjm(Map<String, ChampResult> champs) {
        try {
            ChampResult b = champs.get("BUDGET_GLOBAL");
            ChampResult h = champs.get("HOMMES_MOIS");
            if (b == null || h == null || b.getValeur() == null || h.getValeur() == null) return null;

            double budget = Double.parseDouble(String.valueOf(b.getValeur()).replaceAll("[^0-9.]", ""));
            double hm = Double.parseDouble(String.valueOf(h.getValeur()).replaceAll("[^0-9.]", ""));
            return (hm > 0) ? Math.round(budget / hm / 20.0 * 100.0) / 100.0 : null;
        } catch (Exception e) { return null; }
    }

    /**
     * Génère des alertes de validation croisée.
     */
    private void alertesCroisees(Map<String, ChampResult> champs,
                                 Double tjm,
                                 List<String> alertes,
                                 List<String> bloquantes) {
        // 1. TJM hors plage
        if (tjm != null) {
            if (tjm < tjmMin) {
                alertes.add(String.format("TJM implicite (%.0f €/j) inférieur au seuil Egis (%.0f €/j) — mission potentiellement non rentable", tjm, tjmMin));
            } else if (tjm > tjmMax) {
                alertes.add(String.format("TJM implicite (%.0f €/j) très élevé — vérifier que le budget correspond aux honoraires et non au budget projet total", tjm));
            }
        }

        // 2. PON_TECH + PON_FIN si présents (Phase 2)
        // ... Logique future ici ...

        // 3. VISITE obligatoire détectée
        ChampResult visiteObl = champs.get("VISITE_OBL");
        ChampResult visiteDate = champs.get("VISITE_DATE");
        if (visiteObl != null && "Oui".equalsIgnoreCase(String.valueOf(visiteObl.getValeur()))
                && (visiteDate == null || visiteDate.getValeur() == null)) {
            alertes.add("VISITE obligatoire détectée mais date non extraite — vérifier le TDR manuellement");
        }

        // 4. CONFÉRENCE obligatoire détectée
        ChampResult confObl  = champs.get("CONF_OBL");
        ChampResult confDate = champs.get("CONF_DATE");
        if (confObl != null && "Oui".equalsIgnoreCase(String.valueOf(confObl.getValeur()))
                && (confDate == null || confDate.getValeur() == null)) {
            alertes.add("CONFÉRENCE obligatoire détectée mais date non extraite");
        }

        // 5. Budget faible de confiance
        ChampResult budget = champs.get("BUDGET_GLOBAL");
        if (budget != null && budget.getConfiance() != null && budget.getConfiance() < 0.65) {
            alertes.add("BUDGET_GLOBAL confiance faible (" + Math.round(budget.getConfiance() * 100) + "%) — distinguer honoraires consultants vs budget total projet");
        }

        // 6. HM faible de confiance
        ChampResult hm = champs.get("HOMMES_MOIS");
        if (hm != null && hm.getConfiance() != null && hm.getConfiance() < 0.65) {
            alertes.add("HOMMES_MOIS confiance faible — vérifier qu'il s'agit de H.M et non de mois calendaires");
        }
    }

    private void checkObligatoire(Map<String, ChampResult> champs, String oblKey, String dateKey, String label, List<String> alertes) {
        ChampResult obl = champs.get(oblKey);
        ChampResult date = champs.get(dateKey);
        if (obl != null && "Oui".equalsIgnoreCase(String.valueOf(obl.getValeur())) && (date == null || date.getValeur() == null)) {
            alertes.add(label + " détectée mais date manquante");
        }
    }
}