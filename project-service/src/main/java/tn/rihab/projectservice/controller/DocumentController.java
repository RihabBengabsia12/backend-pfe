package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.projectservice.model.entity.Dossier;
import tn.rihab.projectservice.service.DossierService;
import tn.rihab.projectservice.service.StorageService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dossiers")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DossierService dossierService;
    private final StorageService storageService;


    @GetMapping("/{id}/download/{type}")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable UUID id,
            @PathVariable String type) {

        Dossier dossier = dossierService.findById(id);

        String path = switch (type.toLowerCase()) {
            case "apo"     -> dossier.getApoDocxPath();
            case "methodo" -> dossier.getMethodoDocxPath();
            case "rapport" -> dossier.getRapportPath();
            case "pack"    -> dossier.getPackZipPath();
            case "audit"   -> dossier.getAuditReportPath();
            case "nogo"    -> dossier.getNogoReportPath();
            default        -> throw new IllegalArgumentException(
                    "Type de document inconnu : " + type +
                            ". Valeurs acceptées : apo, methodo, rapport, pack, audit, nogo");
        };

        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "Le document '" + type + "' n'est pas encore disponible pour ce dossier. " +
                            "Statut actuel : " + dossier.getStatus());
        }

        String url      = storageService.getPresignedUrl(path);
        String filename = extractFilename(path, type, id);

        log.info("[Document] URL présignée générée : {} pour dossier {}", type, id);

        return ResponseEntity.ok(Map.of(
                "url",      url,
                "filename", filename,
                "type",     type,
                "path",     path
        ));
    }

    // ── Utilitaire ─────────────────────────────────────────────────────────────

    private String extractFilename(String path, String type, UUID id) {
        // Extraire le nom de fichier depuis le chemin MinIO
        String[] parts = path.split("/");
        String rawName = parts[parts.length - 1];

        // Fallback avec un nom explicite si le fichier n'a pas de nom lisible
        return switch (type.toLowerCase()) {
            case "apo"     -> "APO_"     + id.toString().substring(0, 8) + ".docx";
            case "methodo" -> "Methodologie_" + id.toString().substring(0, 8) + ".docx";
            case "rapport" -> "Rapport_"  + id.toString().substring(0, 8) + ".docx";
            case "pack"    -> "Pack_Soumission_" + id.toString().substring(0, 8) + ".zip";
            case "audit"   -> "Rapport_Audit_" + id.toString().substring(0, 8) + ".docx";
            case "nogo"    -> "Rapport_NoGo_" + id.toString().substring(0, 8) + ".docx";
            default        -> rawName;
        };
    }
}