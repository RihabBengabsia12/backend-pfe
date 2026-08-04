package tn.rihab.adminservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.adminservice.entity.DataEvent;
import tn.rihab.adminservice.service.AuditService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuditController {

    private final AuditService auditService;

    // GET /api/admin/audit/data?userId=&entity=&action=&page=0&size=20
    @GetMapping("/data")
    public ResponseEntity<Page<DataEvent>> getDataEvents(
            @RequestParam(required = false) UUID   userId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                auditService.getDataEvents(userId, entity, action, page, size));
    }
}