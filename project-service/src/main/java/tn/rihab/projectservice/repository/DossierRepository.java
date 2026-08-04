package tn.rihab.projectservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.rihab.projectservice.model.DossierStatus;
import tn.rihab.projectservice.model.entity.Dossier;

import java.util.List;
import java.util.UUID;

@Repository
public interface DossierRepository extends JpaRepository<Dossier, UUID> {

    /**
     * Dashboard principal : tri par priorité ASC (1=urgent en haut)
     * puis par date limite ASC (le plus urgent dans chaque groupe en premier).
     */
    @Query("SELECT d FROM Dossier d ORDER BY d.priorite ASC, d.dtLimSoum ASC NULLS LAST")
    List<Dossier> findAllOrderByPrioriteAndDate();

    List<Dossier> findByStatus(DossierStatus status);

    List<Dossier> findByPays(String pays);

    List<Dossier> findByStatusIn(List<DossierStatus> statuses);

    /** Dossiers actifs (ni clôturés ni archivés) pour le dashboard */
    @Query("""
        SELECT d FROM Dossier d
        WHERE d.status NOT IN ('NO_GO_CONFIRMED','ARCHIVED')
        ORDER BY d.priorite ASC, d.dtLimSoum ASC NULLS LAST
    """)
    List<Dossier> findAllActive();
}