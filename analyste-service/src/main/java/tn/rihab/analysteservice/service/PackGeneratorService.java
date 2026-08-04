package tn.rihab.analysteservice.service;

import tn.rihab.analysteservice.dto.ia.ChecklistResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Compile le pack de soumission complet (Phase 4 → PACK_READY).
 *
 * Contenu du ZIP :
 *   01_APO_<titre>.docx           → APO réglementaire (56 placeholders)
 *   02_Methodologie_<titre>.docx  → Méthodologie en 5 sections
 *   03_Rapport_<titre>.docx       → Rapport général résultat
 *   04_Checklist_Pieces.txt       → Pièces pointées selon bailleur
 *   README.txt                    → Instructions d'utilisation du pack
 *
 * Stocké dans le bucket "packs-soumission" sur MinIO.
 * Le chemin MinIO est publié via AnalysteEventPublisher → project-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PackGeneratorService {

    private final PackStorageService storageService;

    /**
     * Génère le ZIP du pack de soumission.
     *
     * @param dossierId     ID du dossier
     * @param apoDocxPath   Chemin MinIO de l'APO DOCX
     * @param methodoPath   Chemin MinIO de la méthodologie DOCX
     * @param rapportPath   Chemin MinIO du rapport général DOCX
     * @param checklist     Checklist des pièces pointées
     * @param intituleOffre Titre de l'AO (pour nommer le fichier)
     * @return Chemin MinIO du ZIP généré
     */
    public String generatePack(UUID dossierId,
                               String apoDocxPath,
                               String methodoPath,
                               String rapportPath,
                               ChecklistResponseDto checklist,
                               String intituleOffre) {

        log.info("[Pack] Compilation pack ZIP — dossier {}", dossierId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(baos)) {

            // ── 1. APO DOCX ─────────────────────────────────────────────────
            addFileToZip(zip, apoDocxPath,
                    "01_APO_" + sanitize(intituleOffre) + ".docx");

            // ── 2. Méthodologie DOCX ─────────────────────────────────────────
            if (methodoPath != null && !methodoPath.isBlank()) {
                addFileToZip(zip, methodoPath,
                        "02_Methodologie_" + sanitize(intituleOffre) + ".docx");
            }

            // ── 3. Rapport général résultat DOCX ─────────────────────────────
            if (rapportPath != null && !rapportPath.isBlank()) {
                addFileToZip(zip, rapportPath,
                        "03_Rapport_" + sanitize(intituleOffre) + ".docx");
            }

            // ── 4. Checklist pièces pointées (texte) ─────────────────────────
            String checklistTxt = buildChecklistText(checklist);
            zip.putNextEntry(new ZipEntry("04_Checklist_Pieces.txt"));
            zip.write(checklistTxt.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // ── 5. README ─────────────────────────────────────────────────────
            String readme = buildReadme(intituleOffre);
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(readme.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.finish();
            byte[] zipBytes = baos.toByteArray();

            // Stocker le ZIP sur MinIO
            String objectName = dossierId + "/Pack_Soumission_"
                    + sanitize(intituleOffre) + ".zip";

            String path = storageService.uploadBytes(
                    "packs-soumission", objectName, zipBytes, "application/zip");

            log.info("[Pack] ZIP généré : {} ({} Ko)", path, zipBytes.length / 1024);
            return path;

        } catch (Exception e) {
            log.error("[Pack] Erreur génération ZIP : {}", e.getMessage(), e);
            throw new RuntimeException("Génération pack ZIP échouée", e);
        }
    }

    // ── Utilitaires ────────────────────────────────────────────────────────────

    /**
     * Télécharge un fichier depuis MinIO et l'ajoute au ZIP.
     */
    private void addFileToZip(ZipOutputStream zip, String minioPath,
                              String entryName) throws Exception {
        try {
            byte[] bytes = storageService.downloadBytes(minioPath);
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(bytes);
            zip.closeEntry();
        } catch (Exception e) {
            log.warn("[Pack] Fichier non disponible pour ZIP : {} — {}", minioPath, e.getMessage());
            // Ajouter un fichier placeholder pour signaler le manque
            zip.putNextEntry(new ZipEntry(entryName + ".MANQUANT.txt"));
            zip.write(("FICHIER NON DISPONIBLE : " + minioPath).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    /**
     * Génère le texte de la checklist des pièces pointées.
     */
    private String buildChecklistText(ChecklistResponseDto checklist) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CHECKLIST DES PIÈCES À JOINDRE À L'OFFRE ===\n\n");

        if (checklist == null || checklist.getPieces() == null) {
            sb.append("Checklist non disponible — vérifier manuellement selon les exigences du DCE.\n");
            return sb.toString();
        }

        // Pièces obligatoires
        sb.append("── PIÈCES OBLIGATOIRES ──────────────────────────\n");
        checklist.getPieces().stream()
                .filter(p -> Boolean.TRUE.equals(p.getObligatoire()))
                .forEach(p -> sb.append(String.format(
                        "  [x] %s\n      → %s [Source : %s]\n",
                        p.getNom(), p.getDescription(), p.getSource())));

        // Pièces optionnelles
        sb.append("\n── PIÈCES OPTIONNELLES / SI APPLICABLE ──────────\n");
        checklist.getPieces().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getObligatoire()))
                .forEach(p -> sb.append(String.format(
                        "  [ ] %s\n      → %s [Source : %s]\n",
                        p.getNom(), p.getDescription(), p.getSource())));

        sb.append("\n=== FIN CHECKLIST ===\n");
        sb.append("Généré par ProjectIQ — vérifier les exigences spécifiques du DCE.\n");
        return sb.toString();
    }

    /**
     * Génère le fichier README d'instructions du pack.
     */
    private String buildReadme(String intituleOffre) {
        return """
            ╔══════════════════════════════════════════════════════════╗
            ║         PACK DE SOUMISSION — ProjectIQ                  ║
            ╚══════════════════════════════════════════════════════════╝

            Dossier : %s

            CONTENU DU PACK :
            ─────────────────
            01_APO_*.docx          → Analyse Préalable d'Offre (à compléter/signer)
            02_Methodologie_*.docx → Note méthodologique (à enrichir)
            03_Rapport_*.docx      → Rapport général résultat
            04_Checklist_*.txt     → Liste des pièces à joindre

            ÉTAPES AVANT SOUMISSION :
            ─────────────────────────
            1. Valider et compléter les champs manuels de l'APO (en orange dans l'interface)
            2. Enrichir la méthodologie avec les spécificités de votre équipe
            3. Rassembler toutes les pièces de la checklist
            4. Obtenir les signatures requises (DO, DDA, DGA selon le montant)
            5. Déposer l'offre avant la date limite indiquée dans l'APO

            ⚠️ CE DOCUMENT EST CONFIDENTIEL — USAGE INTERNE EGIS UNIQUEMENT

            Généré automatiquement par ProjectIQ-PFE
            """.formatted(intituleOffre != null ? intituleOffre : "Non renseigné");
    }

    private String sanitize(String name) {
        if (name == null) return "dossier";
        return name.replaceAll("[^a-zA-Z0-9À-ÿ_\\-]", "_")
                .substring(0, Math.min(name.length(), 40));
    }
}