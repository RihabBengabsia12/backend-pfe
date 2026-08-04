package tn.rihab.analysteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.analysteservice.model.IaAuditLog;
import tn.rihab.analysteservice.repository.IaAuditLogRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analyses/audit-ia")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IaAuditController {

    private final IaAuditLogRepository auditRepo;

    @GetMapping
    public ResponseEntity<Page<IaAuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)));
    }

    @GetMapping("/dossier/{dossierId}")
    public ResponseEntity<List<IaAuditLog>> getAuditLogsByDossier(@PathVariable UUID dossierId) {
        return ResponseEntity.ok(auditRepo.findByDossierIdOrderByCreatedAtDesc(dossierId));
    }
}
