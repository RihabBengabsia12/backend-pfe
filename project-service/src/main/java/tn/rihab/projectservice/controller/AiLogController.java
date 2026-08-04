package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import tn.rihab.projectservice.model.entity.AiLog;
import tn.rihab.projectservice.repository.AiLogRepository;
import java.util.List;

@RestController
@RequestMapping("/api/dossiers/ai-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AiLogController {

    private final AiLogRepository aiLogRepository;

    @GetMapping
    public ResponseEntity<List<AiLog>> getAllLogs() {
        return ResponseEntity.ok(aiLogRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/dossier/{dossierId}")
    public ResponseEntity<List<AiLog>> getLogsByDossierId(@org.springframework.web.bind.annotation.PathVariable java.util.UUID dossierId) {
        return ResponseEntity.ok(aiLogRepository.findByDossierIdOrderByCreatedAtDesc(dossierId));
    }
}
