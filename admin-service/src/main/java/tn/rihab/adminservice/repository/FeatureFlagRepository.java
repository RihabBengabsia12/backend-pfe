package tn.rihab.adminservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.adminservice.entity.FeatureFlag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
    List<FeatureFlag> findByUserEmail(String userEmail);
    Optional<FeatureFlag> findByUserEmailAndModuleCode(String userEmail, String moduleCode);
}
