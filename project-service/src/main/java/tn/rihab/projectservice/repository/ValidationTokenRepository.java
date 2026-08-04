package tn.rihab.projectservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.rihab.projectservice.model.entity.ValidationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ValidationTokenRepository extends JpaRepository<ValidationToken, UUID> {

    Optional<ValidationToken> findByToken(String token);

    List<ValidationToken> findByDossierId(UUID dossierId);

    List<ValidationToken> findByDossierIdAndStatus(UUID dossierId, String status);

    List<ValidationToken> findByValidateurEmailAndStatus(String email, String status);

    /** Nombre de validations approuvées pour un dossier */
    long countByDossierIdAndStatus(UUID dossierId, String status);

    /** Tokens expirés à nettoyer (job schedulé) */
    List<ValidationToken> findByStatusAndExpiresAtBefore(String status, LocalDateTime now);

    /** Tous les validateurs d'un dossier avec leur statut — pour affichage Angular */
    @Query("SELECT v FROM ValidationToken v WHERE v.dossierId = :dossierId ORDER BY v.createdAt ASC")
    List<ValidationToken> findAllByDossierIdOrdered(UUID dossierId);
}