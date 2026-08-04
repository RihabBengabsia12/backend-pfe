package tn.rihab.analysteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.rihab.analysteservice.model.SystemAuditEntry;
import tn.rihab.analysteservice.service.SystemAuditService;

import java.util.List;

@RestController
@RequestMapping("/api/system-audit")
@RequiredArgsConstructor
public class SystemAuditController {

    private final SystemAuditService systemAuditService;

    @GetMapping
    public ResponseEntity<List<SystemAuditEntry>> getSystemAuditHistory() {
        return ResponseEntity.ok(systemAuditService.getGlobalHistory());
    }

    @org.springframework.web.bind.annotation.PostMapping
    public ResponseEntity<SystemAuditEntry> logSystemAction(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> payload) {
        String actorEmail = payload.getOrDefault("actorEmail", "admin@st2i.com.tn");
        String action = payload.get("action");
        String detail = payload.get("detail");
        SystemAuditEntry entry = systemAuditService.logSystemAction(actorEmail, action, detail);
        return ResponseEntity.ok(entry);
    }
}
