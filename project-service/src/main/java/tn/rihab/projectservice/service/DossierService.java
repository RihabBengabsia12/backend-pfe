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
    private final AnonymizationService anonymizationService;
    private final EventPublisher eventPublisher;
    private final AuditTrailService auditTrailService;
    private final tn.rihab.projectservice.client.AnalysteServiceClient analysteServiceClient;

    // Dans DossierService.java



    @Transactional
    public Dossier createDossierWithAsyncParsing(MultipartFile tdrFile, LocalDate dtLimSoum, Boolean isPrivate, String createdByEmail, String intitule) {
        // 1. Upload du fichier (Stocker)
        String tdrKey = "tdr/" + UUID.randomUUID() + "_" + tdrFile.getOriginalFilename();
        String tdrPath = storageService.uploadFile(MinIOConfig.BUCKET_ORIGINAUX, tdrKey, tdrFile);

        // 2. Cration et enregistrement avec statut UPLOADED
        Dossier dossier = Dossier.builder()
                .documentTdrPath(tdrPath)
                .intituleOffre(intitule)
                .dtLimSoum(dtLimSoum)
                .status(DossierStatus.UPLOADED)
                .isPrivate(isPrivate)
                .createdByEmail(createdByEmail)
                .build();
        dossier = dossierRepository.save(dossier);

        // 3. Lancer le parsing de manire synchrone (maintenant ultra rapide)
        try {
            String tdrText = documentParserService.extractText(tdrPath);
            String textPath = storageService.uploadText(MinIOConfig.BUCKET_TEXTE, dossier.getId() + "/document_full.txt", tdrText);
            dossier.setDocumentTextPath(textPath);
            if (Boolean.TRUE.equals(dossier.getIsPrivate())) {
                String anonymizedText = anonymizationService.maskText(tdrText);
                String anonymizedPath = storageService.uploadText(
                        MinIOConfig.BUCKET_TEXTE,
                        dossier.getId() + "/document_anonymized.txt",
                        anonymizedText
                );
                dossier.setAnonymizedTextPath(anonymizedPath);
                log.info("[DLP] Copie de travail anonymisée créée pour le dossier privé {}", dossier.getId());
            }
            dossierRepository.save(dossier);
            log.info("[Parsing] Extraction termine pour : {}", dossier.getId());
        } catch (Exception e) {
            log.error("[Parsing] Erreur lors de l'extraction : {}", e.getMessage());
        }

        return dossier; // Retourne le dossier avec statut UPLOADED
    }

    // (La mthode runParsingAsync a t retire car l'extraction est maintenant assez rapide pour tre synchrone)

    // 2. NOUVELLE MTHODE : Analyse manuelle (appele par le bouton)
    @Transactional
    public void launchAnalysis(UUID dossierId) {
        Dossier dossier = findById(dossierId);
        String tdrText = getDocumentText(dossierId);

        // Extraction IA
        ExtractionResponseDto extraction = extractionService.extractPhase1(dossierId, tdrText);

        applyExtractionToDossier(dossier, extraction.getChamps());
        dossier.setDtLimSoum(dossier.getDtLimSoum()); // Conserve la date manuelle
        dossier.setTjmImplicite(extraction.getTjmImplicite());
        computePriorite(dossier);

        // Une révalidation de P1 sur un dossier déjà avancé met à jour les
        // métadonnées sans faire régresser son workflow vers INDEXED.
        boolean workflowAlreadyAdvanced = switch (dossier.getStatus()) {
            case DEEP_ANALYSIS, SCORING, MANUAL_INTERVENTION, FORCE_GO, NO_GO_CONFIRMED,
                 MATCHING, DRAFTING, REPORT_GENERATED, PACK_READY, PENDING_VALIDATION,
                 SUBMITTED, AUDIT, ARCHIVED -> true;
            default -> false;
        };
        if (!workflowAlreadyAdvanced) {
            dossier.setStatus(DossierStatus.INDEXED);
        }
        dossierRepository.save(dossier);
        log.info("[Analyse] Analyse IA termine et auto-valide pour le dossier : {}", dossierId);

        // Publier l'vnement RabbitMQ  non bloquant : si RabbitMQ est indisponible,
        // l'analyse reste sauvegarde en BDD (statut INDEXED).
        try {
            eventPublisher.publishDossierIndexed(dossierId);
        } catch (Exception e) {
            log.warn("[Analyse] Publication RabbitMQ choue pour {}  analyse sauvegarde en BDD mais event non publi : {}", dossierId, e.getMessage());
        }
    }

    // --- Les autres mthodes restent inchanges ---
    
    @Transactional
    public void updateStatus(UUID dossierId, DossierStatus status) {
        Dossier dossier = findById(dossierId);
        dossier.setStatus(status);
        dossierRepository.save(dossier);
    }

    @Transactional
    public Dossier validateP1(UUID id, ValidateP1RequestDto req) {
        Dossier dossier = findById(id);
        
        // Sauvegarder la date de dépôt AVANT d'appliquer les champs (ne jamais écraser avec null)
        LocalDate dtLimSoumSaved = dossier.getDtLimSoum();
        
        req.getChamps().forEach((fieldName, champValide) -> {
            metadataRepository.findByDossierIdAndFieldName(id, fieldName)
                    .ifPresent(meta -> {
                        meta.setValeurFinale(champValide.getValeur());
                        meta.setHumanModified(Boolean.TRUE.equals(champValide.getHumanModified()));
                        metadataRepository.save(meta);
                    });
            applyField(dossier, fieldName, champValide.getValeur());
        });
        
        // Si la date limite n'a pas été saisie dans P1 (normal : elle vient du dépôt), on restaure
        if (dossier.getDtLimSoum() == null && dtLimSoumSaved != null) {
            dossier.setDtLimSoum(dtLimSoumSaved);
        }
        
        computePriorite(dossier);
        // Plus de blocage : on log juste un avertissement si des champs critiques manquent
        if (dossier.getPays() == null || dossier.getBudgetGlobal() == null || dossier.getHommesMois() == null) {
            log.warn("[ValidateP1] Dossier {} : champs critiques vides (PAYS={}, BUDGET={}, HM={}) — validation non bloquante",
                    id, dossier.getPays(), dossier.getBudgetGlobal(), dossier.getHommesMois());
        }
        boolean workflowAlreadyAdvanced = switch (dossier.getStatus()) {
            case DEEP_ANALYSIS, SCORING, MANUAL_INTERVENTION, FORCE_GO, NO_GO_CONFIRMED,
                 MATCHING, DRAFTING, REPORT_GENERATED, PACK_READY, PENDING_VALIDATION,
                 SUBMITTED, AUDIT, ARCHIVED -> true;
            default -> false;
        };
        if (!workflowAlreadyAdvanced) {
            dossier.setStatus(DossierStatus.INDEXED);
        }
        Dossier saved = dossierRepository.save(dossier);
        if (!workflowAlreadyAdvanced) {
            eventPublisher.publishDossierIndexed(id);
        }
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
            case "HOMMES_MOIS" -> parseHommesMois(valeur).ifPresent(d::setHommesMois);
            case "DT_LIM_SOUM" -> { try { d.setDtLimSoum(LocalDate.parse(valeur)); } catch (Exception e) {} }
        }
    }

    private void applyExtractionToDossier(Dossier d, Map<String, ChampResult> champs) {
        applyIfPresent(champs, "PAYS", d::setPays);
        applyIfPresent(champs, "INTITULE_OFFRE", d::setIntituleOffre);
        applyIfPresent(champs, "CLIENT", d::setClient);
        applyIfPresent(champs, "HOMMES_MOIS", v -> parseHommesMois(v).ifPresent(d::setHommesMois));
        applyIfPresent(champs, "DT_LIM_SOUM", v -> d.setDtLimSoum(LocalDate.parse(v)));
    }

    private void applyIfPresent(Map<String, ChampResult> champs, String key, java.util.function.Consumer<String> setter) {
        ChampResult r = champs.get(key);
        if (r != null && r.getValeur() != null) setter.accept(String.valueOf(r.getValeur()));
    }

    /**
     * Extrait une seule valeur H/M. L'ancienne logique supprimait tous les
     * caractères non numériques et pouvait transformer une source ambiguë en
     * 220544120. Une valeur ambiguë doit être revue dans la validation P1.
     */
    private java.util.Optional<Double> parseHommesMois(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return java.util.Optional.empty();
        String normalized = rawValue.replace(',', '.');
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<![0-9.])([0-9]+(?:\\.[0-9]+)?)(?![0-9.])")
                .matcher(normalized);
        if (!matcher.find()) {
            log.warn("[H/M] Valeur illisible ignorée : {}", rawValue);
            return java.util.Optional.empty();
        }
        try {
            double value = Double.parseDouble(matcher.group(1));
            if (value <= 0 || value > 10000) {
                log.warn("[H/M] Valeur hors plage ignorée : {} (source: {})", value, rawValue);
                return java.util.Optional.empty();
            }
            if (matcher.find()) {
                log.warn("[H/M] Plusieurs nombres détectés, premier nombre retenu : {} (source: {})", value, rawValue);
            }
            return java.util.Optional.of(value);
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    /** @deprecated Remplacé par un simple log dans validateP1 */
    private void assertBlockingFieldsPresent(Dossier d) {
        // Blocage supprimé — la validation passe même avec des champs incomplets
        log.debug("[assertBlockingFieldsPresent] PAYS={}, BUDGET={}, HM={}, DT={}",
                d.getPays(), d.getBudgetGlobal(), d.getHommesMois(), d.getDtLimSoum());
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
        return dossierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Non trouv"));
    }

    public List<ExtractionMetadata> getExtractionP1(UUID dossierId) {
        return metadataRepository.findByDossierIdOrderByFieldNameAsc(dossierId);
    }

    public String getDocumentText(UUID dossierId) {
        Dossier dossier = findById(dossierId);
        if (dossier.getDocumentTextPath() == null) throw new IllegalStateException("Le texte du document n'est pas disponible.");

        if (!Boolean.TRUE.equals(dossier.getIsPrivate())) {
            return storageService.downloadText(dossier.getDocumentTextPath());
        }

        // Compatibilité avec les dossiers privés créés avant l'ajout de la
        // copie anonymisée persistante : la copie est produite une seule fois.
        if (dossier.getAnonymizedTextPath() == null) {
            String originalText = storageService.downloadText(dossier.getDocumentTextPath());
            String anonymizedText = anonymizationService.maskText(originalText);
            String anonymizedPath = storageService.uploadText(
                    MinIOConfig.BUCKET_TEXTE,
                    dossier.getId() + "/document_anonymized.txt",
                    anonymizedText
            );
            dossier.setAnonymizedTextPath(anonymizedPath);
            dossierRepository.save(dossier);
            log.info("[DLP] Copie de travail anonymisée créée pour le dossier privé existant {}", dossierId);
        }
        return storageService.downloadText(dossier.getAnonymizedTextPath());
    }
    public List<Dossier> getAll() {
        return dossierRepository.findAll();
    }

    @Transactional
    public Dossier updatePriority(UUID id, Integer priorite) {
        Dossier dossier = findById(id);
        dossier.setPriorite(priorite);
        dossier.setPriorityOverride(true);
        auditTrailService.log(id, "PRIORITY_UPDATED", "user", "Nouvelle prioritÃ© : " + priorite, dossier.getStatus());
        return dossierRepository.save(dossier);
    }

    public List<Dossier> getPendingNoGo() {
        return dossierRepository.findAll().stream()
                .filter(d -> (d.getStatus() == DossierStatus.SCORING && Boolean.TRUE.equals(d.getManagerNotified())) ||
                             d.getStatus() == DossierStatus.NO_GO_CONFIRMED)
                .toList();
    }

    @Transactional
    public void notifyManagerNoGo(UUID id) {
        Dossier dossier = findById(id);
        dossier.setManagerNotified(true);
        dossierRepository.save(dossier);
    }

    @Transactional
    public void processNoGoDecision(UUID id, tn.rihab.projectservice.dto.NoGoDecisionRequestDto request) {
        Dossier dossier = findById(id);
        if ("FORCE_GO".equals(request.getDecision())) {
            dossier.setStatus(DossierStatus.MATCHING);
            dossier.setManagerNotified(false);
            auditTrailService.log(id, "FORCE_GO", request.getManagerName(), 
                "Manager forcÃ© Go. Motif: " + request.getJustification(), DossierStatus.MATCHING);
        } else if ("VALIDATE_NOGO".equals(request.getDecision())) {
            dossier.setStatus(DossierStatus.NO_GO_CONFIRMED);
            dossier.setManagerNotified(false);
            auditTrailService.log(id, "NO_GO_VALIDATED", request.getManagerName(), 
                "Manager a validÃ© le No-Go.", DossierStatus.NO_GO_CONFIRMED);
        } else {
            throw new IllegalArgumentException("DÃ©cision invalide");
        }
        
        dossierRepository.save(dossier);

        try {
            analysteServiceClient.processDecision(id, request);
        } catch (Exception e) {
            log.error("Erreur lors de l'appel Ã  analyste-service pour processDecision", e);
        }
    }

    @Transactional
    public void archiveAndAudit(UUID id) {
        Dossier dossier = findById(id);
        if (dossier.getStatus() != DossierStatus.NO_GO_CONFIRMED) {
            throw new IllegalStateException("Le dossier n'est pas NO_GO_CONFIRMED");
        }
        dossier.setStatus(DossierStatus.ARCHIVED);
        dossierRepository.save(dossier);
        
        auditTrailService.log(id, "ARCHIVED", "system", "Archivage suite au No-Go", DossierStatus.ARCHIVED);

        try {
            analysteServiceClient.generateAudit(id);
        } catch (Exception e) {
            log.error("Erreur lors de l'appel Ã  analyste-service pour generateAudit", e);
        }
    }
}
