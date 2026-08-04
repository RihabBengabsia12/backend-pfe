package tn.rihab.analysteservice.service;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.client.IaServiceClient;
import tn.rihab.analysteservice.dto.ia.*;
import tn.rihab.analysteservice.messaging.AnalysteEventPublisher;
import tn.rihab.analysteservice.model.*;
import tn.rihab.analysteservice.repository.ApoDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Assemble l'APO complète (56 placeholders + 9 champs recommandés = 65 champs).
 * Génère les 3 documents :
 *   1. APO-Formulaire-Template.docx  (56 placeholders réglementaires)
 *   2. Methodologie-Template.docx    (5 sections méthodologie)
 *   3. Rapport-Audit-Template.docx   (rapport général résultat)
 * Ensuite génère le pack ZIP de soumission.
 * Publie APO_GENERATED → project-service stocke les URLs MinIO.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApoAssemblyService {

    private final IaServiceClient          iaClient;
    private final DocumentExportService    exportService;
    private final PackGeneratorService     packGenerator;
    private final ChecklistService         checklistService;
    private final AnalysteEventPublisher   publisher;
    private final ApoDataRepository        apoDataRepo;
    private final tn.rihab.analysteservice.client.ProjectServiceClient projectClient;

    /**
     * Point d'entrée principal — appelé depuis AnalyseDeepService après Phase 3.
     * Assemble, génère les 3 documents, compile le pack ZIP, publie l'événement.
     */
    @Transactional
    public void assembleAndGenerate(UUID dossierId,
                                    DossierDto dossier,
                                    AnalyseDossier analyse,
                                    MatchingResult matching,
                                    PwinScore pwin) {
        log.info("[ApoAssembly] Assemblage APO + génération documents — dossier {}", dossierId);

        // 1. Générer les textes narratifs via Claude
        ApoTextsResponseDto texts = generateTextsViaClaude(dossier, analyse, matching, pwin);

        // 2. Construire le dictionnaire ApoData (65 champs)
        ApoData apoData = buildApoData(dossierId, dossier, analyse, matching, pwin, texts);
        apoData = apoDataRepo.save(apoData);

        // 3. Générer l'APO DOCX (56 placeholders réglementaires)
        String apoDocxPath = exportService.exportApo(apoData, dossier.getIntituleOffre());

        // 4. Générer la méthodologie DOCX (5 sections)
        MethodologieResponseDto methodo = generateMethodologieViaClaude(dossier, analyse, matching);
        String methodoPath = exportService.exportMethodologie(methodo, dossier.getIntituleOffre());

        // 5. Générer le rapport général résultat DOCX
        String rapportPath = exportService.exportRapportResultat(apoData, pwin, dossier.getIntituleOffre());

        // 6. Générer la checklist des pièces pointées selon bailleur
        ChecklistResponseDto checklist = checklistService.generate(dossier.getBailleurs(), dossier.getPays());

        // 7. Compiler le pack ZIP (APO + Méthodologie + Rapport + Checklist)
        String packZipPath = packGenerator.generatePack(
                dossierId, apoDocxPath, methodoPath, rapportPath, checklist, dossier.getIntituleOffre());

        // 8. Publier l'événement → project-service stocke les URLs et passe en PACK_READY
        publisher.publishApoGenerated(dossierId, apoDocxPath, methodoPath, rapportPath, packZipPath);

        log.info("[ApoAssembly] Documents générés — APO={}, Méthodo={}, Rapport={}, Pack={}",
                apoDocxPath, methodoPath, rapportPath, packZipPath);
    }

    // ── Assemblage du dictionnaire ApoData ─────────────────────────────────────

    private ApoData buildApoData(UUID dossierId,
                                 DossierDto dossier,
                                 AnalyseDossier analyse,
                                 MatchingResult matching,
                                 PwinScore pwin,
                                 ApoTextsResponseDto texts) {

        ApoData apoData = apoDataRepo.findByDossierId(dossierId)
                .orElse(ApoData.builder().dossierId(dossierId).build());

        Map<String, ApoData.ChampApo> champs = new LinkedHashMap<>();

        // ── Phase 1 — Champs AUTO (récupérés de project-service) ──────────────
        put(champs, "PAYS",               dossier.getPays(),           "AUTO", "project-service");
        put(champs, "INTITULE_OFFRE",     dossier.getIntituleOffre(),  "AUTO", "project-service");
        put(champs, "NUMERO_REFERENCE",   dossier.getNumeroReference(),"AUTO", "project-service");
        put(champs, "CLIENT",             dossier.getClient(),         "AUTO", "project-service");
        put(champs, "LANGUE",             dossier.getLangue(),         "AUTO", "project-service");
        put(champs, "BAILLEURS",          dossier.getBailleurs(),      "AUTO", "project-service");
        put(champs, "BUDGET_GLOBAL",      dossier.getBudgetGlobal(),   "AUTO", "project-service");
        put(champs, "HOMMES_MOIS",        fmt(dossier.getHommesMois()),"AUTO", "project-service");
        put(champs, "DT_LIM_SOUM",        fmt(dossier.getDtLimSoum()), "AUTO", "project-service");
        put(champs, "ARRIVEE_BO",         fmt(dossier.getArriveBo()),  "AUTO", "project-service");
        put(champs, "TRANSMISSION",       fmt(dossier.getTransmission()),"AUTO","project-service");
        put(champs, "VISITE_OBL",         bool(dossier.getVisiteObl()),"AUTO", "project-service");
        put(champs, "VISITE_DATE",        fmt(dossier.getVisiteDate()),"AUTO", "project-service");
        put(champs, "CONF_OBL",           bool(dossier.getConfObl()),  "AUTO", "project-service");
        put(champs, "CONF_DATE",          fmt(dossier.getConfDate()),  "AUTO", "project-service");

        // ── Phase 2 — Champs extraits par Claude ──────────────────────────────
        put(champs, "NOTE_MINIMALE",          analyse.getNoteMinimale(),       "CLAUDE", "claude-api");
        put(champs, "PON_TECH",               fmt(analyse.getPonTech()),       "CLAUDE", "claude-api");
        put(champs, "PON_FIN",                fmt(analyse.getPonFin()),        "CLAUDE", "claude-api");
        put(champs, "DELAI_GLOBAL_MOIS",      fmt(analyse.getDelaiGlobalMois()),"CLAUDE","claude-api");
        put(champs, "DATE_LIMITE_SOUMISSION", fmt(analyse.getDateLimiteSoumission()),"CLAUDE","claude-api");
        put(champs, "DATE_LIMITE_QUESTIONS",  fmt(analyse.getDateLimiteQuestions()), "CLAUDE","claude-api");
        put(champs, "FIN_LOCAL_OUI_NON",      analyse.getFinLocalOuiNon(),    "CLAUDE", "claude-api");
        put(champs, "FINA_LOCAL_DETAILS",     analyse.getFinaLocalDetails(),  "CLAUDE", "claude-api");
        put(champs, "CAUTION_MONTANT",        analyse.getCautionMontant(),    "CLAUDE", "claude-api");
        put(champs, "CAUTION_MONNAIE",        analyse.getCautionMonnaie(),    "CLAUDE", "claude-api");
        put(champs, "CAUTION_DUREE",          analyse.getCautionDuree(),      "CLAUDE", "claude-api");
        put(champs, "BANQUE_LOCALE_EXIGEE",   analyse.getBanqueLocaleExigee(),"CLAUDE", "claude-api");
        put(champs, "DELAI_PREP_SUF",         analyse.getDelaiPrepSuf(),      "CALCULATED","analyste-service");
        put(champs, "JUSTIF_DELAI_PREP",      analyse.getJustifDelaiPrep(),   "CALCULATED","analyste-service");

        // ── 10 Risques (niveau extrait par Claude, validé par humain) ─────────
        putRisque(champs, "RISQUE_PAYS_SECURITE",         analyse.getRisquePaysSecurite());
        putRisque(champs, "RISQUES_FINANCIERS",           analyse.getRisquesFinanciers());
        putRisque(champs, "PENALITES",                    analyse.getPenalites());
        putRisque(champs, "EXIGENCES_TDR_INACCEPTABLES",  analyse.getExigencesTdrInacceptables());
        putRisque(champs, "GARANTIES_ASSURANCES_ELEVEES", analyse.getGarantiesAssurancesElevees());
        putRisque(champs, "TAILLE_DISPERSION",            analyse.getTailleDispersion());
        putRisque(champs, "FRAIS_DIVERS_ELEVES",          analyse.getFraisDiversEleves());
        putRisque(champs, "BUDGET_FAIBLE_HM_LIMITES",     analyse.getBudgetFaibleHmLimites());
        putRisque(champs, "PARTICIPATION_LOCALE_EXCESSIVE",analyse.getParticipationLocaleExcessive());
        putRisque(champs, "FISCALITE_NON_MAITRISEE",      analyse.getFiscaliteNonMaitrisee());

        // ── Phase 3 — Champs issus du matching ────────────────────────────────
        if (matching != null) {
            put(champs, "REFS_EXIGEES",           matching.getRefsExigees(),   "CALCULATED","analyste-referentiel");
            put(champs, "GAP_REFS",               matching.getGapRefs(),       "CALCULATED","analyste-referentiel");
            put(champs, "EXPERTS_REQUIS",         matching.getExpertsRequis(), "CALCULATED","analyste-referentiel");
            put(champs, "TAUX_COUVERTURE_EXPERTS",fmt(matching.getTauxCouvertureExperts()),"CALCULATED","analyste-referentiel");
            put(champs, "RELATION_CLIENT",        fmt(matching.getRelationClientNiveau()), "CALCULATED","analyste-referentiel");
            put(champs, "QUALIFS_EXIGEES",        matching.getQualifsExigees(),"CALCULATED","analyste-referentiel");
            put(champs, "ALIGNEMENT_STRATEGIQUE", matching.getAlignementStrategique(), "CALCULATED","analyste-referentiel");
            put(champs, "ANALYSE_CONCURRENCE",    decapsulate(dossierId, texts.getAnalyseConcurrence()),"CLAUDE","claude-api");
            put(champs, "JUSTIF_SHORTLIST",       decapsulate(dossierId, texts.getJustifShortlist()),  "CLAUDE","claude-api");
        }

        // ── Champs calculés automatiquement ───────────────────────────────────
        put(champs, "PWIN_SCORE",     pwin.getScoreGlobal() != null
                ? String.format("%.1f%%", pwin.getScoreGlobal()) : "N/A", "CALCULATED","analyste-service");
        put(champs, "TJM_IMPLICITE",  dossier.getTjmImplicite() != null
                ? String.format("%.0f €/j", dossier.getTjmImplicite()) : "N/A", "CALCULATED","project-service");

        // ── Phase 4 — Textes générés par Claude ───────────────────────────────
        put(champs, "RESUME_CONTEXTE_OBJECTIFS", decapsulate(dossierId, texts.getResumeContexteObjectifs()), "CLAUDE","claude-api");
        put(champs, "POINTS_CRITIQUES",          decapsulate(dossierId, texts.getPointsCritiques()),          "CLAUDE","claude-api");
        put(champs, "RECOMMANDATION_GO_NOGO",    decapsulate(dossierId, texts.getRecommandationGoNogo()),     "CLAUDE","claude-api");
        put(champs, "ARGUMENTAIRE_GO_NOGO",      decapsulate(dossierId, texts.getArgumentaireGoNogo()),       "CLAUDE","claude-api");
        put(champs, "LISTE_CLARIFICATIONS",      decapsulate(dossierId, texts.getListeClarifications()),      "CLAUDE","claude-api");

        // ── Champs MANUAL (saisie humaine obligatoire) ────────────────────────
        put(champs, "BUDGET_INTERNE",        "",  "MANUAL", "user");
        put(champs, "SOURCE_BUDGET_INTERNE", "",  "MANUAL", "user");
        put(champs, "CAPACITE_DELAI",        analyse.getCapaciteDelai()       != null ? analyse.getCapaciteDelai()       : "", "MANUAL","user");
        put(champs, "JUSTIF_CAPACITE_DELAI", analyse.getJustifCapaciteDelai() != null ? analyse.getJustifCapaciteDelai() : "", "MANUAL","user");
        put(champs, "PLAN_ACTION",           "",  "MANUAL", "user");
        put(champs, "PARTENAIRES",           "",  "MANUAL", "user");
        put(champs, "CHEF_DE_FILE",          "",  "MANUAL", "user");
        put(champs, "ROLES_REPARTITION",     "",  "MANUAL", "user");
        put(champs, "SHORTLIST",             "",  "MANUAL", "user");
        put(champs, "SHORTLIST_EQUILIBREE",  "",  "MANUAL", "user");

        // Calcul complétude
        long remplis = champs.values().stream()
                .filter(c -> c.getValeur() != null && !c.getValeur().isBlank())
                .count();
        double completeness = (double) remplis / champs.size() * 100;

        apoData.setChamps(champs);
        apoData.setCompleteness(Math.round(completeness * 10.0) / 10.0);
        apoData.setUpdatedAt(LocalDateTime.now());

        log.info("[ApoAssembly] ApoData — {}/{} champs remplis ({:.1f}%)",
                remplis, champs.size(), completeness);

        return apoData;
    }

    private String decapsulate(UUID dossierId, String text) {
        if (text == null || text.isBlank()) return text;
        try {
            return projectClient.decapsulate(dossierId, text);
        } catch (Exception e) {
            log.error("[ApoAssembly] Erreur lors de la décapsulation DLP pour le dossier {}", dossierId, e);
            return text; // Retourne le texte original si le service project-service échoue
        }
    }

    // ── Génération des textes Claude ───────────────────────────────────────────

    private ApoTextsResponseDto generateTextsViaClaude(DossierDto dossier,
                                                       AnalyseDossier analyse,
                                                       MatchingResult matching,
                                                       PwinScore pwin) {
        ApoGenerationContextDto context = buildGenerationContext(dossier, analyse, matching, pwin);
        return iaClient.generateApoTexts(context);
    }

    private MethodologieResponseDto generateMethodologieViaClaude(DossierDto dossier,
                                                                  AnalyseDossier analyse,
                                                                  MatchingResult matching) {
        ApoGenerationContextDto context = buildGenerationContext(dossier, analyse, matching, null);
        return iaClient.generateMethodologie(context);
    }

    private ApoGenerationContextDto buildGenerationContext(DossierDto dossier,
                                                           AnalyseDossier analyse,
                                                           MatchingResult matching,
                                                           PwinScore pwin) {
        List<ApoGenerationContextDto.RisqueContexte> risques = buildRisquesContexte(analyse);

        return ApoGenerationContextDto.builder()
                .dossierId(dossier.getId())
                .intituleOffre(dossier.getIntituleOffre())
                .client(dossier.getClient())
                .pays(dossier.getPays())
                .bailleurs(dossier.getBailleurs())
                .budgetGlobal(dossier.getBudgetGlobal())
                .hommesMois(fmt(dossier.getHommesMois()))
                .dtLimSoum(fmt(dossier.getDtLimSoum()))
                .langue(dossier.getLangue())
                .pwinScore(pwin != null ? pwin.getScoreGlobal() : null)
                .decisionAuto(pwin != null ? pwin.getDecisionAuto() : null)
                .risques(risques)
                .secteurAo(matching != null ? matching.getSecteurAo() : null)
                .tauxCouvertureCompetences(matching != null ? matching.getTauxCouvertureCompetences() : null)
                .tauxCouvertureExperts(matching != null ? matching.getTauxCouvertureExperts() : null)
                .gapRefs(matching != null ? matching.getGapRefs() : null)
                .matriceDiff(matching != null ? matching.getMatriceDiff() : null)
                .relationClientNiveau(matching != null ? matching.getRelationClientNiveau() : null)
                .delaiGlobalMois(fmt(analyse.getDelaiGlobalMois()))
                .tjmImplicite(fmt(dossier.getTjmImplicite()))
                .build();
    }

    // ── Getters publics (pour controllers) ────────────────────────────────────

    public ApoData getApoData(UUID dossierId) {
        return apoDataRepo.findByDossierId(dossierId)
                .orElseThrow(() -> new IllegalArgumentException("ApoData non trouvée : " + dossierId));
    }

    @Transactional
    public ApoData updateField(UUID dossierId, String fieldName, String valeur) {
        ApoData apoData = getApoData(dossierId);
        apoData.getChamps().put(fieldName, ApoData.ChampApo.builder()
                .valeur(valeur).statut("MANUAL").source("user").build());

        long remplis = apoData.getChamps().values().stream()
                .filter(c -> c.getValeur() != null && !c.getValeur().isBlank()).count();
        apoData.setCompleteness((double) remplis / apoData.getChamps().size() * 100);
        apoData.setUpdatedAt(LocalDateTime.now());
        return apoDataRepo.save(apoData);
    }

    // ── Utilitaires ────────────────────────────────────────────────────────────

    private void put(Map<String, ApoData.ChampApo> map, String key,
                     String val, String statut, String source) {
        map.put(key, ApoData.ChampApo.builder()
                .valeur(val != null ? val : "")
                .statut(val != null && !val.isBlank() ? statut : "EMPTY")
                .source(source).build());
    }

    private void putRisque(Map<String, ApoData.ChampApo> map, String key, String risqueVal) {
        if (risqueVal == null) { put(map, key, "", "EMPTY", ""); return; }
        int idx = risqueVal.indexOf("||");
        String niveau = idx >= 0 ? risqueVal.substring(0, idx).trim() : risqueVal.trim();
        put(map, key, niveau, "CLAUDE", "claude-api");
    }

    private List<ApoGenerationContextDto.RisqueContexte> buildRisquesContexte(AnalyseDossier a) {
        List<ApoGenerationContextDto.RisqueContexte> list = new ArrayList<>();
        addR(list, "RISQUE_PAYS_SECURITE",          a.getRisquePaysSecurite());
        addR(list, "RISQUES_FINANCIERS",             a.getRisquesFinanciers());
        addR(list, "PENALITES",                      a.getPenalites());
        addR(list, "EXIGENCES_TDR_INACCEPTABLES",   a.getExigencesTdrInacceptables());
        addR(list, "GARANTIES_ASSURANCES_ELEVEES",   a.getGarantiesAssurancesElevees());
        addR(list, "TAILLE_DISPERSION",              a.getTailleDispersion());
        addR(list, "FRAIS_DIVERS_ELEVES",            a.getFraisDiversEleves());
        addR(list, "BUDGET_FAIBLE_HM_LIMITES",       a.getBudgetFaibleHmLimites());
        addR(list, "PARTICIPATION_LOCALE_EXCESSIVE",  a.getParticipationLocaleExcessive());
        addR(list, "FISCALITE_NON_MAITRISEE",        a.getFiscaliteNonMaitrisee());
        return list;
    }

    private void addR(List<ApoGenerationContextDto.RisqueContexte> list, String nom, String val) {
        if (val == null) return;
        int idx = val.indexOf("||");
        list.add(ApoGenerationContextDto.RisqueContexte.builder()
                .nom(nom)
                .niveau(idx >= 0 ? val.substring(0, idx).trim() : val.trim())
                .justification(idx >= 0 ? val.substring(idx + 2).trim() : "")
                .build());
    }

    private String fmt(Object v) { return v != null ? v.toString() : ""; }
    private String bool(Boolean v) { return v != null ? (v ? "Oui" : "Non") : ""; }
}