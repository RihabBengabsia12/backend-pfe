package tn.rihab.analysteservice.repository;

import tn.rihab.analysteservice.model.Competence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** CRUD référentiel Compétences — module admin /api/referentiel/competences. */
@Repository
public interface CompetenceRepository extends JpaRepository<Competence, UUID> {
    List<Competence> findByActifTrue();
    List<Competence> findByDomaineIgnoreCase(String domaine);
}