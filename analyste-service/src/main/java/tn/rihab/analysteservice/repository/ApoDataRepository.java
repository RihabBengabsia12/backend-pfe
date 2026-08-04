package tn.rihab.analysteservice.repository;

import tn.rihab.analysteservice.model.ApoData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** CRUD ApoData — dictionnaire des 65 champs APO, 1 ligne par dossier. */
@Repository
public interface ApoDataRepository extends JpaRepository<ApoData, UUID> {
    Optional<ApoData> findByDossierId(UUID dossierId);
}