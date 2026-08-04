package tn.rihab.analysteservice.repository;

import tn.rihab.analysteservice.model.ExpertProfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** CRUD référentiel Experts — module admin /api/referentiel/experts. */
@Repository
public interface ExpertProfilRepository extends JpaRepository<ExpertProfil, UUID> {

    List<ExpertProfil> findByActifTrue();

    /**
     * Experts disponibles sur une période donnée — utilisé par ExpertsMatcher
     * pour calculer TAUX_COUVERTURE_EXPERTS.
     */
    @Query("""
        SELECT e FROM ExpertProfil e
        WHERE e.actif = true
          AND e.disponibleDu <= :debut
          AND e.disponibleAu >= :fin
    """)
    List<ExpertProfil> findDisponibles(LocalDate debut, LocalDate fin);
}