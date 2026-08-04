package tn.rihab.adminservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.adminservice.DTO.FeatureFlagDTO;
import tn.rihab.adminservice.service.FeatureFlagService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/features")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @GetMapping("/{email}")
    public ResponseEntity<Map<String, Boolean>> getUserFeatures(@PathVariable String email) {
        return ResponseEntity.ok(featureFlagService.getUserFeatures(email));
    }

    @PostMapping("/{email}")
    public ResponseEntity<Void> updateUserFeatures(
            @PathVariable String email,
            @RequestBody List<FeatureFlagDTO> featureUpdates) {
        featureFlagService.updateUserFeatures(email, featureUpdates);
        return ResponseEntity.ok().build();
    }
}
