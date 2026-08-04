package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.projectservice.model.entity.Delegation;
import tn.rihab.projectservice.service.DelegationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/delegations")
@RequiredArgsConstructor
public class DelegationController {

    private final DelegationService delegationService;
    private final tn.rihab.projectservice.service.SystemAuditClientService systemAudit;

    @GetMapping
    public ResponseEntity<List<Delegation>> getAllDelegations() {
        return ResponseEntity.ok(delegationService.getAllDelegations());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Delegation> updateDelegation(@PathVariable UUID id, @RequestBody Delegation delegation) {
        Delegation updated = delegationService.updateDelegation(id, delegation);
        systemAudit.logSystemAction("DELEGATION_UPDATE", "Seuil modifié pour le rôle " + updated.getRoleName() + " à " + updated.getThreshold() + " TND");
        return ResponseEntity.ok(updated);
    }
}
