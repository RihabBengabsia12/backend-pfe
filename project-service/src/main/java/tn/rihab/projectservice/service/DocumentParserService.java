package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// GESTION PDF
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
// GESTION WORD
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import org.springframework.stereotype.Service;
import java.io.InputStream;


@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentParserService {

    private final StorageService storageService;


    public String extractText(String minioPath) {
        log.info("[Parser] Extraction texte depuis : {}", minioPath);
        try (InputStream stream = storageService.getStream(minioPath)) {
            String lower = minioPath.toLowerCase();
            if (lower.endsWith(".pdf")) {
                return extractFromPdf(stream);
            } else if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
                return extractFromDocx(stream);
            } else {
                throw new IllegalArgumentException("Format non supporté : " + minioPath);
            }
        } catch (Exception e) {
            log.error("[Parser] Erreur extraction {}: {}", minioPath, e.getMessage(), e);
            throw new RuntimeException("Extraction texte échouée : " + minioPath, e);
        }
    }

    // ── Extraction PDF via Apache PDFBox ────────────────────────────────────
    private String extractFromPdf(InputStream stream) throws Exception {
        // CORRECTION ICI : Utilisation de Loader.loadPDF avec le tableau d'octets
        try (PDDocument doc = Loader.loadPDF(stream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false); // OPTIMISATION MAJEURE : false = extraction instantanée (O(N)), true = très lent (O(N^2))
            String raw = stripper.getText(doc);
            log.info("[Parser] PDF : {} pages, {} caractères", doc.getNumberOfPages(), raw.length());
            return clean(raw);
        }
    }

    // ── Extraction DOCX via Apache POI ──────────────────────────────────────
    private String extractFromDocx(InputStream stream) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(stream);
             XWPFWordExtractor ext = new XWPFWordExtractor(doc)) {
            ext.setFetchHyperlinks(false);
            String raw = ext.getText();
            log.info("[Parser] DOCX : {} caractères", raw.length());
            return clean(raw);
        }
    }


    private String clean(String raw) {
        return raw
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .replaceAll("\\r\\n|\\r", "\n")
                .replaceAll("\\n{4,}", "\n\n\n")
                .trim();
    }
}