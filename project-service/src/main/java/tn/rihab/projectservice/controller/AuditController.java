package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.projectservice.model.entity.AuditEntry;
import tn.rihab.projectservice.service.AuditTrailService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dossiers")
@RequiredArgsConstructor
public class AuditController {

    private final AuditTrailService auditTrailService;


    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditEntry>> getAuditHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(auditTrailService.getHistory(id));
    }
}