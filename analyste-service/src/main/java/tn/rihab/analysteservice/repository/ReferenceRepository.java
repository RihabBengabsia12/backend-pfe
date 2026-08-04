package tn.rihab.analysteservice.repository;

import tn.rihab.analysteservice.model.Reference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** CRUD référentiel Références projets — module admin /api/referentiel/references. */
@Repository
public interface ReferenceRepository extends JpaRepository<Reference, UUID> {
    List<Reference> findByActifTrue();
    List<Reference> findBySecteurIgnoreCaseAndActifTrue(String secteur);
    List<Reference> findByPaysIgnoreCaseAndActifTrue(String pays);
}