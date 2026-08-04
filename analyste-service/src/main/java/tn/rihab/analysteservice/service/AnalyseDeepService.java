package tn.rihab.analysteservice.service;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.client.IaServiceClient;
import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.dto.ia.*;
import tn.rihab.analysteservice.matching.MatchingEngine;
import tn.rihab.analysteservice.messaging.AnalysteEventPublisher;
import tn.rihab.analysteservice.model.*;
import tn.rihab.analysteservice.repository.*;
import tn.rihab.analysteservice.scoring.ScoringEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestre le pipeline complet déclenché par l'événement DOSSIER_INDEXED.
 *
 * Séquence asynchrone :
 *   Phase 2a : extraction des 16 champs bloquants majeurs
 *   Phase 2b : extraction et évaluation des 10 risques
 *   Phase 2c : calcul du score P-Win (5 axes)
 *   Phase 2d : si NO_GO → génération rapport No-Go + publication ScoringCompleted
 *              si GO/MANUAL → publication ScoringCompleted → continue
 *   Phase 3  : matching référentiel (compétences, références, experts, client)
 *   Phase 4  : assemblage APO + génération des 3 documents + pack ZIP
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyseDeepService {

    private final ProjectServiceClient    projectClient;
    private final IaServiceClient         iaClient;
    private final ScoringEngine           scoringEngine;
    private final MatchingEngine          matchingEngine;
    private final ApoAssemblyService      apoAssemblyService;
    private final NoGoReportService       noGoReportService;
    private final AnalysteEventPublisher  publisher;
    private final AnalyseDossierRepository analyseRepo;
    private final PwinScoreRepository     pwinRepo;
    private final IaAuditLogRepository    auditLogRepo;
    private final SseService              sseService;

    @Async
    public void preExtractPhase2(UUID dossierId) {
        log.info("[Pipeline] Pré-extraction Phase 2 pour dossier {}", dossierId);
        try {
            String documentText = projectClient.getDocumentText(dossierId);
            
            AnalyseDossier analyse = analyseRepo.findByDossierId(dossierId).orElse(null);
            if (analyse == null || analyse.getNoteMinimale() == null) {
                analyse = extractPhase2(dossierId, documentText);
            }
            
            if (analyse.getRisquesFinanciers() == null || analyse.getRisquesFinanciers().isEmpty()) {
                extractRisks(dossierId, analyse, documentText);
            }
            
            sseService.sendEvent(dossierId, "GLOBAL_EXTRACTION_READY", "Les extractions Phase 1 et 2 sont prêtes à être validées.");
            log.info("[Pipeline] Pré-extraction Phase 2 terminée pour dossier {}", dossierId);
        } catch (Exception e) {
            log.error("[Pipeline] Erreur pré-extraction dossier {} : {}", dossierId, e.getMessage(), e);
        }
    }

    /**
     * Point d'entrée du pipeline — appelé par AnalysteEventConsumer.
     * Exécuté de façon asynchrone pour ne pas bloquer RabbitMQ.
     */
    @Async
    public void runFullPipeline(UUID dossierId) {
        log.info("[Pipeline] Démarrage Phase 2-4 pour dossier {}", dossierId);

        try {
            sseService.sendEvent(dossierId, "PIPELINE_START", "Démarrage du pipeline asynchrone");

            // 1. Récupérer les données Phase 1 et le texte du document
            DossierDto dossier = projectClient.getDossier(dossierId);
            String documentText = projectClient.getDocumentText(dossierId);
            sseService.sendEvent(dossierId, "DATA_LOADED", "Données du dossier chargées");

            // 2. Extraction Phase 2 — 16 champs majeurs
            AnalyseDossier analyse = analyseRepo.findByDossierId(dossierId).orElse(null);
            if (analyse == null || analyse.getNoteMinimale() == null) {
                analyse = extractPhase2(dossierId, documentText);
            }
            sseService.sendEvent(dossierId, "PHASE2_COMPLETED", "Extraction Phase 2 terminée");

            // 3. Extraction + évaluation des 10 risques
            if (analyse.getRisquesFinanciers() == null || analyse.getRisquesFinanciers().isEmpty()) {
                extractRisks(dossierId, analyse, documentText);
            }
            sseService.sendEvent(dossierId, "RISKS_COMPLETED", "Analyse des risques terminée");

            // 4. Calcul du score P-Win
            PwinScore pwin = scoringEngine.calculate(dossier, analyse, null);
            sseService.sendEvent(dossierId, "PWIN_COMPLETED", pwin);

            // 5. Publier le résultat du scoring → project-service met à jour le statut
            publisher.publishScoringCompleted(dossierId, pwin.getScoreGlobal(), pwin.getDecisionAuto());

            // 6. Si NO_GO → générer rapport No-Go, publier, s'arrêter
            if ("NO_GO".equals(pwin.getDecisionAuto())
                    || "MANUAL".equals(pwin.getDecisionAuto())) {

                // Génération automatique du rapport No-Go via template DOCX
                noGoReportService.generate(dossierId, dossier, pwin, analyse);

                // Publier scoring.completed → project-service passe en MANUAL_INTERVENTION
                // Le payload contient decision=NO_GO pour que project-service
                // sache qu'il faut afficher la page No-Go/Force-Go côté Angular
                publisher.publishScoringCompleted(
                        dossierId,
                        pwin.getScoreGlobal(),
                        pwin.getDecisionAuto()  // "NO_GO" ou "MANUAL"
                );

                log.warn("[Pipeline] Dossier {} — P-Win={:.1f}% → {} — rapport No-Go généré",
                        dossierId, pwin.getScoreGlobal(), pwin.getDecisionAuto());
                sseService.sendEvent(dossierId, "PIPELINE_STOPPED_NOGO", pwin.getDecisionAuto());
                return;
                // Pipeline s'arrête ici — pas de Phase 3/4
                // Reprend uniquement si l'utilisateur appelle force-go
            }

            // 7. Phase 3 — Matching référentiel
            MatchingResult matching = matchingEngine.runMatching(dossier, documentText);
            publisher.publishMatchingCompleted(dossierId,
                    matching.getTauxCouvertureCompetences() != null ? matching.getTauxCouvertureCompetences() : 0,
                    matching.getTauxCouvertureExperts()     != null ? matching.getTauxCouvertureExperts()     : 0);
            sseService.sendEvent(dossierId, "MATCHING_COMPLETED", matching);

            // 8. Recalculer le P-Win avec le matching (Axe A et D sont affinés)
            pwin = scoringEngine.calculate(dossier, analyse, matching);

            // 9. Phase 4 — Assemblage APO + génération des documents
            apoAssemblyService.assembleAndGenerate(dossierId, dossier, analyse, matching, pwin);
            sseService.sendEvent(dossierId, "APO_COMPLETED", "Génération des documents APO terminée");

            log.info("[Pipeline] Phase 2-4 terminée pour dossier {}", dossierId);
            sseService.sendEvent(dossierId, "PIPELINE_COMPLETED", "Pipeline complet terminé avec succès");

        } catch (Exception e) {
            log.error("[Pipeline] Erreur fatale pour dossier {} : {}", dossierId, e.getMessage(), e);
            sseService.sendEvent(dossierId, "PIPELINE_ERROR", e.getMessage());
            throw new RuntimeException("Pipeline analyste échoué pour dossier " + dossierId, e);
        }
    }

    /**
     * Recalcule automatiquement Phase 2c (Score P-Win), Phase 3 (Matching) 
     * et Phase 4 (APO) suite à une correction manuelle (Phase 1, 2 ou 3).
     */
    @Async
    public void recalculatePipeline(UUID dossierId) {
        log.info("[Pipeline] Recalcul automatique Phase 2-4 pour dossier {}", dossierId);
        try {
            sseService.sendEvent(dossierId, "RECALCUL_START", "Démarrage du recalcul asynchrone");

            DossierDto dossier = projectClient.getDossier(dossierId);
            String documentText = projectClient.getDocumentText(dossierId);
            AnalyseDossier analyse = getAnalyse(dossierId);

            // Recalcul du score P-Win
            PwinScore pwin = scoringEngine.calculate(dossier, analyse, null);
            sseService.sendEvent(dossierId, "PWIN_COMPLETED", pwin);
            publisher.publishScoringCompleted(dossierId, pwin.getScoreGlobal(), pwin.getDecisionAuto());

            if ("NO_GO".equals(pwin.getDecisionAuto()) || "MANUAL".equals(pwin.getDecisionAuto())) {
                noGoReportService.generate(dossierId, dossier, pwin, analyse);
                sseService.sendEvent(dossierId, "PIPELINE_STOPPED_NOGO", pwin.getDecisionAuto());
                return;
            }

            // Recalcul du matching
            MatchingResult matching = matchingEngine.runMatching(dossier, documentText);
            publisher.publishMatchingCompleted(dossierId,
                    matching.getTauxCouvertureCompetences() != null ? matching.getTauxCouvertureCompetences() : 0,
                    matching.getTauxCouvertureExperts()     != null ? matching.getTauxCouvertureExperts()     : 0);
            sseService.sendEvent(dossierId, "MATCHING_COMPLETED", matching);

            pwin = scoringEngine.calculate(dossier, analyse, matching);

            // Régénération APO
            apoAssemblyService.assembleAndGenerate(dossierId, dossier, analyse, matching, pwin);
            sseService.sendEvent(dossierId, "APO_COMPLETED", "Génération des documents APO terminée");
            sseService.sendEvent(dossierId, "RECALCUL_COMPLETED", "Recalcul complet terminé avec succès");

        } catch (Exception e) {
            log.error("[Pipeline] Erreur fatale lors du recalcul pour dossier {} : {}", dossierId, e.getMessage(), e);
            sseService.sendEvent(dossierId, "PIPELINE_ERROR", e.getMessage());
        }
    }

    // ── Phase 2a : Extraction des 16 champs majeurs ────────────────────────────

    @Transactional
    public AnalyseDossier extractPhase2(UUID dossierId, String documentText) {
        log.info("[AnalyseDeep] Extraction Phase 2 — dossier {}", dossierId);

        DossierDto dossier = projectClient.getDossier(dossierId);
        String textToSend = documentText;
        if (Boolean.TRUE.equals(dossier.getIsPrivate())) {
            log.info("[Anonymization] Application du masque DLP sur le texte P2");
            textToSend = projectClient.maskText(documentText);
        }

        ExtractionRequestDto req = ExtractionRequestDto.builder()
                .dossierId(dossierId)
                .documentText(textToSend)
                .phase("P2")
                .build();

        ExtractionResponseDto resp = iaClient.extractPhase2(req);

        // Sauvegarder l'audit
        if (resp.getToken_usage() != null) {
            auditLogRepo.save(IaAuditLog.builder()
                    .dossierId(dossierId)
                    .actionName("EXTRACTION_PHASE_2")
                    .tokenUsage(resp.getToken_usage())
                    .processingTimeMs(resp.getProcessing_time_ms())
                    .estimatedCost(resp.getEstimated_cost())
                    .cacheCreationTokens(resp.getCache_creation_tokens())
                    .cacheReadTokens(resp.getCache_read_tokens())
                    .build());
        }

        AnalyseDossier analyse = analyseRepo.findByDossierId(dossierId)
                .orElse(AnalyseDossier.builder().dossierId(dossierId).build());

        // Appliquer les champs extraits
        Map<String, ChampResultDto> champs = resp.getChamps();
        
        if (Boolean.TRUE.equals(dossier.getIsPrivate()) && champs != null) {
            // Unmask logic
            java.util.Map<String, String> valuesToUnmask = new java.util.HashMap<>();
            champs.forEach((k, v) -> {
                if (v.getValeur() != null) {
                    valuesToUnmask.put(k + "_val", v.getValeur());
                }
                if (v.getSource() != null) {
                    valuesToUnmask.put(k + "_src", v.getSource());
                }
            });
            java.util.Map<String, String> unmasked = projectClient.unmaskMap(valuesToUnmask);
            champs.forEach((k, v) -> {
                if (unmasked.containsKey(k + "_val")) {
                    v.setValeur(unmasked.get(k + "_val"));
                }
                if (unmasked.containsKey(k + "_src")) {
                    v.setSource(unmasked.get(k + "_src"));
                }
            });
        }

        applyIfPresent(champs, "NOTE_MINIMALE",           v -> analyse.setNoteMinimale(v));
        applyIfPresent(champs, "DATE_LIMITE_QUESTIONS",   v -> { try { analyse.setDateLimiteQuestions(java.time.LocalDate.parse(v)); } catch (Exception ig) {} });
        applyIfPresent(champs, "DELAI_GLOBAL_MOIS",       v -> { try { analyse.setDelaiGlobalMois(Integer.parseInt(v.replaceAll("[^0-9]", ""))); } catch (Exception ig) {} });
        applyIfPresent(champs, "FIN_LOCAL_OUI_NON",       v -> analyse.setFinLocalOuiNon(v));
        applyIfPresent(champs, "FINA_LOCAL_DETAILS",      v -> analyse.setFinaLocalDetails(v));
        applyIfPresent(champs, "CAUTION_MONTANT",         v -> analyse.setCautionMontant(v));
        applyIfPresent(champs, "CAUTION_MONNAIE",         v -> analyse.setCautionMonnaie(v));
        applyIfPresent(champs, "CAUTION_DUREE",           v -> analyse.setCautionDuree(v));
        applyIfPresent(champs, "BANQUE_LOCALE_EXIGEE",    v -> analyse.setBanqueLocaleExigee(v));
        applyIfPresent(champs, "PON_TECH",                v -> { try { analyse.setPonTech(Double.parseDouble(v.replaceAll("[^0-9.]", ""))); } catch (Exception ig) {} });
        applyIfPresent(champs, "PON_FIN",                 v -> { try { analyse.setPonFin(Double.parseDouble(v.replaceAll("[^0-9.]", ""))); } catch (Exception ig) {} });
        applyIfPresent(champs, "LISTE_CLARIFICATIONS",    v -> {}); // stocké dans ApoData uniquement

        // DATE_LIMITE_SOUMISSION synchronisée avec DT_LIM_SOUM Phase 1
        applyIfPresent(champs, "DATE_LIMITE_SOUMISSION",  v -> { try { analyse.setDateLimiteSoumission(java.time.LocalDate.parse(v)); } catch (Exception ig) {} });

        // Calcul automatique DELAI_PREP_SUF
        if (analyse.getDateLimiteSoumission() != null) {
            long jours = java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.now(), analyse.getDateLimiteSoumission());
            analyse.setDelaiPrepSuf(jours >= 15 ? "Oui" : "Non");
            analyse.setJustifDelaiPrep(jours >= 15
                    ? jours + " jours ouvrables disponibles — délai suffisant"
                    : "Seulement " + jours + " jours restants — délai insuffisant");
        }

        return analyseRepo.save(analyse);
    }

    // ── Phase 2b : Extraction et évaluation des 10 risques ────────────────────

    @Transactional
    public AnalyseDossier extractRisks(UUID dossierId,
                                       AnalyseDossier analyse,
                                       String documentText) {
        log.info("[AnalyseDeep] Extraction risques — dossier {}", dossierId);

        DossierDto dossier = projectClient.getDossier(dossierId);
        String textToSend = documentText;
        if (Boolean.TRUE.equals(dossier.getIsPrivate())) {
            textToSend = projectClient.maskText(documentText);
        }

        ExtractionRequestDto req = ExtractionRequestDto.builder()
                .dossierId(dossierId)
                .documentText(textToSend)
                .phase("RISKS")
                .build();

        RiskAnalysisResponseDto resp = iaClient.extractRisks(req);
        
        // Sauvegarder l'audit
        if (resp.getToken_usage() != null) {
            auditLogRepo.save(IaAuditLog.builder()
                    .dossierId(dossierId)
                    .actionName("ANALYSE_RISQUES")
                    .tokenUsage(resp.getToken_usage())
                    .processingTimeMs(resp.getProcessing_time_ms())
                    .estimatedCost(resp.getEstimated_cost())
                    .cacheCreationTokens(resp.getCache_creation_tokens())
                    .cacheReadTokens(resp.getCache_read_tokens())
                    .build());
        }
        
        Map<String, RiskItemDto> risques = resp.getRisques();
        
        if (Boolean.TRUE.equals(dossier.getIsPrivate()) && risques != null) {
            java.util.Map<String, String> valuesToUnmask = new java.util.HashMap<>();
            risques.forEach((k, v) -> {
                if (v.getJustification() != null) {
                    valuesToUnmask.put(k, v.getJustification());
                }
            });
            java.util.Map<String, String> unmasked = projectClient.unmaskMap(valuesToUnmask);
            risques.forEach((k, v) -> {
                if (unmasked.containsKey(k)) {
                    v.setJustification(unmasked.get(k));
                }
            });
        }

        // Stocker chaque risque au format "NIVEAU||justification"
        if (risques.containsKey("RISQUE_PAYS_SECURITE"))
            analyse.setRisquePaysSecurite(formatRisque(risques.get("RISQUE_PAYS_SECURITE")));
        if (risques.containsKey("RISQUES_FINANCIERS"))
            analyse.setRisquesFinanciers(formatRisque(risques.get("RISQUES_FINANCIERS")));
        if (risques.containsKey("PENALITES"))
            analyse.setPenalites(formatRisque(risques.get("PENALITES")));
        if (risques.containsKey("EXIGENCES_TDR_INACCEPTABLES"))
            analyse.setExigencesTdrInacceptables(formatRisque(risques.get("EXIGENCES_TDR_INACCEPTABLES")));
        if (risques.containsKey("GARANTIES_ASSURANCES_ELEVEES"))
            analyse.setGarantiesAssurancesElevees(formatRisque(risques.get("GARANTIES_ASSURANCES_ELEVEES")));
        if (risques.containsKey("TAILLE_DISPERSION"))
            analyse.setTailleDispersion(formatRisque(risques.get("TAILLE_DISPERSION")));
        if (risques.containsKey("FRAIS_DIVERS_ELEVES"))
            analyse.setFraisDiversEleves(formatRisque(risques.get("FRAIS_DIVERS_ELEVES")));
        if (risques.containsKey("BUDGET_FAIBLE_HM_LIMITES"))
            analyse.setBudgetFaibleHmLimites(formatRisque(risques.get("BUDGET_FAIBLE_HM_LIMITES")));
        if (risques.containsKey("PARTICIPATION_LOCALE_EXCESSIVE"))
            analyse.setParticipationLocaleExcessive(formatRisque(risques.get("PARTICIPATION_LOCALE_EXCESSIVE")));
        if (risques.containsKey("FISCALITE_NON_MAITRISEE"))
            analyse.setFiscaliteNonMaitrisee(formatRisque(risques.get("FISCALITE_NON_MAITRISEE")));

        return analyseRepo.save(analyse);
    }

    // ── Getters utilisés par les controllers ──────────────────────────────────

    public AnalyseDossier getAnalyse(UUID dossierId) {
        return analyseRepo.findByDossierId(dossierId)
                .orElseThrow(() -> new IllegalArgumentException("Analyse non trouvée pour dossier : " + dossierId));
    }

    @Transactional
    public AnalyseDossier updateAnalyse(UUID dossierId, AnalyseDossier updated) {
        AnalyseDossier existing = getAnalyse(dossierId);
        // Mise à jour des champs manuels saisis par l'analyste
        existing.setCapaciteDelai(updated.getCapaciteDelai());
        existing.setJustifCapaciteDelai(updated.getJustifCapaciteDelai());
        existing.setTransmission(updated.getTransmission());
        // Champs risques corrigés manuellement
        if (updated.getRisquePaysSecurite()        != null) existing.setRisquePaysSecurite(updated.getRisquePaysSecurite());
        if (updated.getRisquesFinanciers()          != null) existing.setRisquesFinanciers(updated.getRisquesFinanciers());
        if (updated.getPenalites()                  != null) existing.setPenalites(updated.getPenalites());
        if (updated.getExigencesTdrInacceptables()  != null) existing.setExigencesTdrInacceptables(updated.getExigencesTdrInacceptables());
        if (updated.getGarantiesAssurancesElevees() != null) existing.setGarantiesAssurancesElevees(updated.getGarantiesAssurancesElevees());
        if (updated.getTailleDispersion()           != null) existing.setTailleDispersion(updated.getTailleDispersion());
        if (updated.getFraisDiversEleves()          != null) existing.setFraisDiversEleves(updated.getFraisDiversEleves());
        if (updated.getBudgetFaibleHmLimites()      != null) existing.setBudgetFaibleHmLimites(updated.getBudgetFaibleHmLimites());
        if (updated.getParticipationLocaleExcessive() != null) existing.setParticipationLocaleExcessive(updated.getParticipationLocaleExcessive());
        if (updated.getFiscaliteNonMaitrisee()      != null) existing.setFiscaliteNonMaitrisee(updated.getFiscaliteNonMaitrisee());
        return analyseRepo.save(existing);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private String formatRisque(RiskItemDto item) {
        if (item == null) return "Inconnu||Non évalué";
        return (item.getNiveau() != null ? item.getNiveau() : "Inconnu")
                + "||"
                + (item.getJustification() != null ? item.getJustification() : "");
    }

    private void applyIfPresent(Map<String, ChampResultDto> champs,
                                String key,
                                java.util.function.Consumer<String> setter) {
        ChampResultDto r = champs.get(key);
        if (r != null && r.getValeur() != null) setter.accept(r.getValeur());
    }
}