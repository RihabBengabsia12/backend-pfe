package tn.rihab.projectservice.model;

public enum DossierStatus {
    // Phase 1
    UPLOADED, PARSING_INITIAL, CORRECTION_LOOP, INDEXED,
    // Phase 2
    DEEP_ANALYSIS, SCORING, MANUAL_INTERVENTION, FORCE_GO, NO_GO_CONFIRMED,
    // Phase 3
    MATCHING,
    // Phase 4
    DRAFTING, REPORT_GENERATED, PACK_READY,
    // Phase 5
    PENDING_VALIDATION, SUBMITTED,
    // Phase 6
    AUDIT, ARCHIVED,
    // Erreurs
    ERROR
}