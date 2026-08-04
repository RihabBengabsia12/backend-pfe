package tn.rihab.adminservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.adminservice.DTO.FeatureFlagDTO;
import tn.rihab.adminservice.entity.FeatureFlag;
import tn.rihab.adminservice.repository.FeatureFlagRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final AuditService auditService; // To log the changes

    private static final List<String> DEFAULT_MODULES = List.of(
            "ESPACE_ANALYSTE",
            "LIVRABLES",
            "LOGS_IA",
            "ESPACE_DECISION",
            "ESPACE_SUPERVISION"
    );

    @Transactional(readOnly = true)
    public Map<String, Boolean> getUserFeatures(String email) {
        List<FeatureFlag> flags = featureFlagRepository.findByUserEmail(email);
        
        // If empty, return defaults (all true)
        if (flags.isEmpty()) {
            return DEFAULT_MODULES.stream().collect(Collectors.toMap(m -> m, m -> true));
        }
        
        // Merge with defaults in case a new module is added later
        Map<String, Boolean> result = DEFAULT_MODULES.stream().collect(Collectors.toMap(m -> m, m -> true));
        for (FeatureFlag f : flags) {
            result.put(f.getModuleCode(), f.isEnabled());
        }
        return result;
    }

    @Transactional
    public void updateUserFeatures(String email, List<FeatureFlagDTO> featureUpdates) {
        for (FeatureFlagDTO update : featureUpdates) {
            FeatureFlag flag = featureFlagRepository.findByUserEmailAndModuleCode(email, update.getModuleCode())
                    .orElseGet(() -> FeatureFlag.builder()
                            .userEmail(email)
                            .moduleCode(update.getModuleCode())
                            .build());

            flag.setEnabled(update.isEnabled());
            flag.setUpdatedAt(LocalDateTime.now());
            featureFlagRepository.save(flag);
        }

        // Log the override
        auditService.saveAudit(
                null,
                "USER_FEATURE_FLAGS",
                "UPDATE",
                "Mise à jour des droits spécifiques",
                "L'utilisateur " + email + " a eu ses droits mis à jour."
        );
    }
}
