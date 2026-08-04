package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.rihab.projectservice.config.MinIOConfig;
import tn.rihab.projectservice.dto.ChampResult;
import tn.rihab.projectservice.dto.ExtractionResponseDto;
import tn.rihab.projectservice.dto.ValidateP1RequestDto;
import tn.rihab.projectservice.messaging.EventPublisher;
import tn.rihab.projectservice.model.DossierStatus;
import tn.rihab.projectservice.model.entity.Dossier;
import tn.rihab.projectservice.model.entity.ExtractionMetadata;
import tn.rihab.projectservice.repository.DossierRepository;
import tn.rihab.projectservice.repository.ExtractionMetadataRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DossierService {

    private final DossierRepository dossierRepository;
    private final ExtractionMetadataRepository metadataRepository;
    private final StorageService storageService;
    private final DocumentParserService documentParserService;
    private final ExtractionService extractionService;
    private final EventPublisher eventPublisher;
    private final AuditTrailService auditTrailService;

    // Dans DossierService.java



    @Transactional
    public Dossier createDossierWithAsyncParsing(MultipartFile tdrFile, LocalDate dtLimSoum, Boolean isPrivate, String createdByEmail, String intitule) {
        // 1. Upload du fichier (Stocker)
        String tdrKey = "tdr/" + UUID.randomUUID() + "_" + tdrFile.getOriginalFilename();
        String tdrPath = storageService.uploadFile(MinIOConfig.BUCKET_ORIGINAUX, tdrKey, tdrFile);

        // 2. Création et enregistrement avec statut UPLOADED
        Dossier dossier = Dossier.builder()
                .documentTdrPath(tdrPath)
                .intituleOffre(intitule)
                .dtLimSoum(dtLimSoum)
                .status(DossierStatus.UPLOADED)
                .isPrivate(isPrivate)
                .createdByEmail(createdByEmail)
                .build();
        dossier = dossierRepository.save(dossier);

        // 3. Lancer le parsing de manière synchrone (maintenant ultra rapide)
        try {
            String tdrText = documentParserService.extractText(tdrPath);
            String textPath = storageService.uploadText(MinIOConfig.BUCKET_TEXTE, dossier.getId() + "/document_full.txt", tdrText);
            dossier.setDocumentTextPath(textPath);
            dossierRepository.save(dossier);
            log.info("[Parsing] Extraction terminée pour : {}", dossier.getId());
        } catch (Exception e) {
            log.error("[Parsing] Erreur lors de l'extraction : {}", e.getMessage());
        }

        return dossier; // Retourne le dossier avec statut UPLOADED
    }

    // (La méthode runParsingAsync a été retirée car l'extraction est maintenant assez rapide pour être synchrone)

    // 2. NOUVELLE MÉTHODE : Analyse manuelle (appelée par le bouton)
    @Transactional
    public void launchAnalysis(UUID dossierId) {
        Dossier dossier = findById(dossierId);
        String tdrText = storageService.downloadText(dossier.getDocumentTextPath());

        // Extraction IA
        ExtractionResponseDto extraction = extractionService.extractPhase1(dossierId, tdrText);

        applyExtractionToDossier(dossier, extraction.getChamps());
        dossier.setDtLimSoum(dossier.getDtLimSoum()); // Conserve la date manuelle
        dossier.setTjmImplicite(extraction.getTjmImplicite());
        computePriorite(dossier);

        dossier.setStatus(DossierStatus.INDEXED);
        dossierRepository.save(dossier);
        log.info("[Analyse] Analyse IA terminée et auto-validée pour le dossier : {}", dossierId);

        // Publier l'événement RabbitMQ — non bloquant : si RabbitMQ est indisponible,
        // l'analyse reste sauvegardée en BDD (statut INDEXED).
        try {
            eventPublisher.publishDossierIndexed(dossierId);
        } catch (Exception e) {
            log.warn("[Analyse] Publication RabbitMQ échouée pour {} — analyse sauvegardée en BDD mais event non publié : {}", dossierId, e.getMessage());
        }
    }

    // --- Les autres méthodes restent inchangées ---
    
    @Transactional
    public void updateStatus(UUID dossierId, DossierStatus status) {
        Dossier dossier = findById(dossierId);
        dossier.setStatus(status);
        dossierRepository.save(dossier);
    }

    @Transactional
    public Dossier validateP1(UUID id, ValidateP1RequestDto req) {
        Dossier dossier = findById(id);
        req.getChamps().forEach((fieldName, champValide) -> {
            metadataRepository.findByDossierIdAndFieldName(id, fieldName)
                    .ifPresent(meta -> {
                        meta.setValeurFinale(champValide.getValeur());
                        meta.setHumanModified(Boolean.TRUE.equals(champValide.getHumanModified()));
                        metadataRepository.save(meta);
                    });
            applyField(dossier, fieldName, champValide.getValeur());
        });
        computePriorite(dossier);
        assertBlockingFieldsPresent(dossier);
        dossier.setStatus(DossierStatus.INDEXED);
        Dossier saved = dossierRepository.save(dossier);
        eventPublisher.publishDossierIndexed(id);
        return saved;
    }

    private void computePriorite(Dossier d) {
        if (d.getDtLimSoum() != null) {
            int jours = countBusinessDays(LocalDate.now(), d.getDtLimSoum());
            d.setJoursOuvrables(jours);
            if (!Boolean.TRUE.equals(d.getPriorityOverride())) {
                d.setPriorite(jours <= 10 ? 1 : jours <= 20 ? 2 : 3);
            }
        }
        if (d.getBudgetGlobal() != null && d.getHommesMois() != null && d.getHommesMois() > 0) {
            try {
                double budget = Double.parseDouble(d.getBudgetGlobal().replaceAll("[^0-9.]", ""));
                d.setTjmImplicite(Math.round(budget / d.getHommesMois() / 20.0 * 100.0) / 100.0);
            } catch (Exception e) { log.warn("Erreur calcul TJM"); }
        }
    }

    private void applyField(Dossier d, String fieldName, String valeur) {
        if (valeur == null) return;
        switch (fieldName) {
            case "PAYS" -> d.setPays(valeur);
            case "INTITULE_OFFRE" -> d.setIntituleOffre(valeur);
            case "CLIENT" -> d.setClient(valeur);
            case "HOMMES_MOIS" -> { try { d.setHommesMois(Double.parseDouble(valeur.replaceAll("[^0-9.]", ""))); } catch (Exception e) {} }
            case "DT_LIM_SOUM" -> { try { d.setDtLimSoum(LocalDate.parse(valeur)); } catch (Exception e) {} }
        }
    }

    private void applyExtractionToDossier(Dossier d, Map<String, ChampResult> champs) {
        applyIfPresent(champs, "PAYS", d::setPays);
        applyIfPresent(champs, "INTITULE_OFFRE", d::setIntituleOffre);
        applyIfPresent(champs, "CLIENT", d::setClient);
        applyIfPresent(champs, "HOMMES_MOIS", v -> d.setHommesMois(Double.parseDouble(v.replaceAll("[^0-9.]", ""))));
        applyIfPresent(champs, "DT_LIM_SOUM", v -> d.setDtLimSoum(LocalDate.parse(v)));
    }

    private void applyIfPresent(Map<String, ChampResult> champs, String key, java.util.function.Consumer<String> setter) {
        ChampResult r = champs.get(key);
        if (r != null && r.getValeur() != null) setter.accept(String.valueOf(r.getValeur()));
    }

    private void assertBlockingFieldsPresent(Dossier d) {
        if (d.getPays() == null || d.getBudgetGlobal() == null || d.getHommesMois() == null || d.getDtLimSoum() == null)
            throw new IllegalStateException("Champs bloquants manquants");
    }

    private int countBusinessDays(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) count++;
            d = d.plusDays(1);
        }
        return count;
    }

    public Dossier findById(UUID id) {
        return dossierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Non trouvé"));
    }

    public List<ExtractionMetadata> getExtractionP1(UUID dossierId) {
        return metadataRepository.findByDossierIdOrderByFieldNameAsc(dossierId);
    }

    public String getDocumentText(UUID dossierId) {
        Dossier dossier = findById(dossierId);
        if (dossier.getDocumentTextPath() == null) throw new IllegalStateException("Le texte du document n'est pas disponible.");
        return storageService.downloadText(dossier.getDocumentTextPath());
    }

    public List<Dossier> getAll() {
        return dossierRepository.findAll();
    }

    @Transactional
    public Dossier updatePriority(UUID id, Integer priorite) {
        Dossier dossier = findById(id);
        dossier.setPriorite(priorite);
        dossier.setPriorityOverride(true);
        auditTrailService.log(id, "PRIORITY_UPDATED", "user", "Nouvelle priorité : " + priorite, dossier.getStatus());
        return dossierRepository.save(dossier);
    }
}