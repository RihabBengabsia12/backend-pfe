package tn.rihab.analysteservice.controller;

import tn.rihab.analysteservice.model.Competence;
import tn.rihab.analysteservice.model.ExpertProfil;
import tn.rihab.analysteservice.model.Reference;
import tn.rihab.analysteservice.repository.CompetenceRepository;
import tn.rihab.analysteservice.repository.ExpertProfilRepository;
import tn.rihab.analysteservice.repository.ReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CRUD du référentiel société Egis — module d'administration.
 * Base URL : /api/referentiel
 *
 * Pré-requis bloquant : sans ce référentiel rempli, le matching Phase 3
 * et le scoring complet (Axe A, D) ne peuvent pas fonctionner correctement.
 * Prévoir un jeu de données initial avant les premiers tests du pipeline.
 *
 * /api/referentiel/competences    CRUD compétences sectorielles
 * /api/referentiel/references     CRUD projets passés (pour GAP_REFS)
 * /api/referentiel/experts        CRUD experts mobilisables
 * /api/referentiel/qualifications Liste des qualifications/certifications Egis détenues
 */
@RestController
@RequestMapping("/api/analyses/referentiel")
@RequiredArgsConstructor
@Slf4j
public class ReferentielController {

    private final CompetenceRepository  competenceRepo;
    private final ReferenceRepository   referenceRepo;
    private final ExpertProfilRepository expertRepo;
    private final tn.rihab.analysteservice.service.SystemAuditService systemAuditService;

    // ════════════════════════════════════════════════════════════
    // COMPÉTENCES
    // ════════════════════════════════════════════════════════════

    @GetMapping("/competences")
    public ResponseEntity<List<Competence>> listCompetences(
            @RequestParam(required = false) String domaine) {
        if (domaine != null && !domaine.isBlank()) {
            return ResponseEntity.ok(competenceRepo.findByDomaineIgnoreCase(domaine));
        }
        return ResponseEntity.ok(competenceRepo.findByActifTrue());
    }

    @GetMapping("/competences/{id}")
    public ResponseEntity<Competence> getCompetence(@PathVariable UUID id) {
        return ResponseEntity.ok(competenceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compétence non trouvée : " + id)));
    }

    @PostMapping("/competences")
    public ResponseEntity<Competence> createCompetence(@RequestBody Competence competence) {
        log.info("[Referentiel] Ajout nouvelle compétence: {}", competence.getSousDomaine());
        if (competence.getDomaine() == null || competence.getDomaine().isBlank()) {
            throw new IllegalArgumentException("Le domaine est obligatoire");
        }
        if (competence.getNiveau() == null) competence.setNiveau("CONFIRME");
        competence.setActif(true);
        Competence saved = competenceRepo.save(competence);
        log.info("[Référentiel] Compétence créée : {} ({})", saved.getDomaine(), saved.getNiveau());
        
        systemAuditService.logSystemAction("admin@st2i.com.tn", "ADD_COMPETENCE", 
            "Ajout de la compétence: " + saved.getSousDomaine());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/competences/{id}")
    public ResponseEntity<Competence> updateCompetence(
            @PathVariable UUID id, @RequestBody Competence updated) {
        Competence existing = competenceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compétence non trouvée : " + id));
        existing.setDomaine(updated.getDomaine());
        existing.setSousDomaine(updated.getSousDomaine());
        existing.setNiveau(updated.getNiveau());
        existing.setMotsCles(updated.getMotsCles());
        return ResponseEntity.ok(competenceRepo.save(existing));
    }

    @DeleteMapping("/competences/{id}")
    public ResponseEntity<Void> deleteCompetence(@PathVariable UUID id) {
        // Soft delete — on désactive plutôt que supprimer (préserve l'historique matching)
        Competence existing = competenceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compétence non trouvée : " + id));
        existing.setActif(false);
        competenceRepo.save(existing);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════
    // RÉFÉRENCES (projets passés)
    // ════════════════════════════════════════════════════════════

    @GetMapping("/references")
    public ResponseEntity<List<Reference>> listReferences(
            @RequestParam(required = false) String secteur,
            @RequestParam(required = false) String pays) {
        if (secteur != null && !secteur.isBlank()) {
            return ResponseEntity.ok(referenceRepo.findBySecteurIgnoreCaseAndActifTrue(secteur));
        }
        if (pays != null && !pays.isBlank()) {
            return ResponseEntity.ok(referenceRepo.findByPaysIgnoreCaseAndActifTrue(pays));
        }
        return ResponseEntity.ok(referenceRepo.findByActifTrue());
    }

    @GetMapping("/references/{id}")
    public ResponseEntity<Reference> getReference(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Référence non trouvée : " + id)));
    }

    @PostMapping("/references")
    public ResponseEntity<Reference> createReference(@RequestBody Reference reference) {
        if (reference.getTitre() == null || reference.getTitre().isBlank()) {
            throw new IllegalArgumentException("Le titre du projet est obligatoire");
        }
        reference.setActif(true);
        Reference saved = referenceRepo.save(reference);
        log.info("[Référentiel] Référence créée : {} ({}, {})",
                saved.getTitre(), saved.getPays(), saved.getAnnee());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/references/{id}")
    public ResponseEntity<Reference> updateReference(
            @PathVariable UUID id, @RequestBody Reference updated) {
        Reference existing = referenceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Référence non trouvée : " + id));
        existing.setTitre(updated.getTitre());
        existing.setClient(updated.getClient());
        existing.setPays(updated.getPays());
        existing.setSecteur(updated.getSecteur());
        existing.setBailleur(updated.getBailleur());
        existing.setAnnee(updated.getAnnee());
        existing.setDureeMois(updated.getDureeMois());
        existing.setBudgetEuros(updated.getBudgetEuros());
        existing.setHommesMois(updated.getHommesMois());
        existing.setDescriptionCourte(updated.getDescriptionCourte());
        return ResponseEntity.ok(referenceRepo.save(existing));
    }

    @DeleteMapping("/references/{id}")
    public ResponseEntity<Void> deleteReference(@PathVariable UUID id) {
        Reference existing = referenceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Référence non trouvée : " + id));
        existing.setActif(false);
        referenceRepo.save(existing);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════
    // EXPERTS
    // ════════════════════════════════════════════════════════════

    @GetMapping("/experts")
    public ResponseEntity<List<ExpertProfil>> listExperts(
            @RequestParam(required = false) String debut,
            @RequestParam(required = false) String fin) {
        if (debut != null && fin != null) {
            return ResponseEntity.ok(expertRepo.findDisponibles(
                    java.time.LocalDate.parse(debut), java.time.LocalDate.parse(fin)));
        }
        return ResponseEntity.ok(expertRepo.findByActifTrue());
    }

    @GetMapping("/experts/{id}")
    public ResponseEntity<ExpertProfil> getExpert(@PathVariable UUID id) {
        return ResponseEntity.ok(expertRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expert non trouvé : " + id)));
    }

    @PostMapping("/experts")
    public ResponseEntity<ExpertProfil> createExpert(@RequestBody ExpertProfil expert) {
        if (expert.getNom() == null || expert.getNom().isBlank()) {
            throw new IllegalArgumentException("Le nom de l'expert est obligatoire");
        }
        if (expert.getDisponibleDu() != null && expert.getDisponibleAu() != null
                && expert.getDisponibleDu().isAfter(expert.getDisponibleAu())) {
            throw new IllegalArgumentException("disponibleDu doit être antérieur à disponibleAu");
        }
        expert.setActif(true);
        ExpertProfil saved = expertRepo.save(expert);
        log.info("[Référentiel] Expert créé : {} ({})", saved.getNom(), saved.getSpecialites());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/experts/{id}")
    public ResponseEntity<ExpertProfil> updateExpert(
            @PathVariable UUID id, @RequestBody ExpertProfil updated) {
        ExpertProfil existing = expertRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expert non trouvé : " + id));
        existing.setNom(updated.getNom());
        existing.setSpecialites(updated.getSpecialites());
        existing.setLangues(updated.getLangues());
        existing.setAnneesExperience(updated.getAnneesExperience());
        existing.setDisponibleDu(updated.getDisponibleDu());
        existing.setDisponibleAu(updated.getDisponibleAu());
        existing.setTauxJournalier(updated.getTauxJournalier());
        return ResponseEntity.ok(expertRepo.save(existing));
    }

    @DeleteMapping("/experts/{id}")
    public ResponseEntity<Void> deleteExpert(@PathVariable UUID id) {
        ExpertProfil existing = expertRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expert non trouvé : " + id));
        existing.setActif(false);
        expertRepo.save(existing);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════
    // QUALIFICATIONS / CERTIFICATIONS (vue dérivée des compétences EXPERT/REFERENCE)
    // ════════════════════════════════════════════════════════════

    /**
     * Retourne la liste des qualifications détenues par Egis (utilisé pour
     * comparer avec [[QUALIFS_EXIGEES]] et construire [[GAP_QUALIFS]] en Phase 3).
     * Vue simplifiée basée sur les compétences de niveau EXPERT ou REFERENCE.
     */
    @GetMapping("/qualifications")
    public ResponseEntity<List<String>> listQualifications() {
        List<String> qualifs = competenceRepo.findByActifTrue().stream()
                .filter(c -> "EXPERT".equalsIgnoreCase(c.getNiveau())
                        || "REFERENCE".equalsIgnoreCase(c.getNiveau()))
                .map(c -> c.getDomaine() + (c.getSousDomaine() != null ? " — " + c.getSousDomaine() : ""))
                .distinct()
                .toList();
        return ResponseEntity.ok(qualifs);
    }
}