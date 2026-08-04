package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.projectservice.model.entity.AnonymizationDict;
import tn.rihab.projectservice.repository.AnonymizationDictRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DlpService {

    private final AnonymizationDictRepository dictRepository;
    private final AuditTrailService auditTrailService;

    /**
     * Tâche planifiée pour régénérer les codes de tous les mots actifs.
     * S'exécute tous les jours à minuit.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void scheduledCodeRegeneration() {
        log.info("[DLP] Début de la régénération quotidienne des codes d'anonymisation.");
        regenerateAllCodes();
        log.info("[DLP] Fin de la régénération quotidienne des codes.");
    }

    @Transactional
    public void regenerateAllCodes() {
        List<AnonymizationDict> activeWords = dictRepository.findByIsActiveTrue();
        for (AnonymizationDict dict : activeWords) {
            dict.setReplacementCode(generateRandomCode());
            dictRepository.save(dict);
        }
        log.info("[DLP] {} codes régénérés avec succès.", activeWords.size());
    }

    private String generateRandomCode() {
        String uuidPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "***CODE_" + uuidPart + "***";
    }

    public List<AnonymizationDict> getAll() {
        return dictRepository.findAll();
    }

    @Transactional
    public AnonymizationDict addWord(String originalWord) {
        if (dictRepository.existsByOriginalWordIgnoreCase(originalWord)) {
            throw new IllegalArgumentException("Ce mot existe déjà dans le dictionnaire.");
        }
        AnonymizationDict dict = AnonymizationDict.builder()
                .originalWord(originalWord.trim())
                .replacementCode(generateRandomCode())
                .isActive(true)
                .build();
        return dictRepository.save(dict);
    }

    @Transactional
    public AnonymizationDict toggleActive(Long id, boolean isActive) {
        AnonymizationDict dict = dictRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mot introuvable avec l'ID: " + id));
        dict.setIsActive(isActive);
        return dictRepository.save(dict);
    }

    @Transactional
    public void deleteWord(Long id) {
        dictRepository.deleteById(id);
    }

    /**
     * Encapsule le texte brut en remplaçant les mots actifs par leurs codes.
     */
    public String encapsulate(String text, UUID dossierId) {
        if (text == null || text.isBlank()) {
            return text;
        }

        List<AnonymizationDict> activeWords = dictRepository.findByIsActiveTrue();
        if (activeWords.isEmpty()) {
            return text;
        }

        String encapsulatedText = text;
        int totalReplaced = 0;

        for (AnonymizationDict dict : activeWords) {
            if (dict.getReplacementCode() == null || dict.getReplacementCode().isBlank()) {
                dict.setReplacementCode(generateRandomCode());
                dictRepository.save(dict);
            }

            // Utilisation d'une regex pour remplacer le mot entier de façon insensible à la casse
            // Pattern.quote échappe les caractères spéciaux pour éviter les erreurs de syntaxe
            String regex = "(?i)\\b" + Pattern.quote(dict.getOriginalWord()) + "\\b";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(encapsulatedText);
            
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            
            if (count > 0) {
                encapsulatedText = matcher.replaceAll(dict.getReplacementCode());
                totalReplaced += count;
            }
        }

        if (dossierId != null && totalReplaced > 0) {
            auditTrailService.log(
                    dossierId, 
                    "DLP_ENCAPSULATION", 
                    "system", 
                    String.format("Texte encapsulé avant envoi IA : %d remplacements effectués pour protéger des mots sensibles.", totalReplaced), 
                    null
            );
        } else if (dossierId != null) {
            auditTrailService.log(
                    dossierId, 
                    "DLP_CHECK", 
                    "system", 
                    "Vérification DLP effectuée. Aucun mot sensible du dictionnaire trouvé dans le texte.", 
                    null
            );
        }

        return encapsulatedText;
    }

    /**
     * Décapsule le texte en remplaçant les codes par les mots originaux.
     * Cette méthode parcourt tous les mots du dictionnaire (actifs ou inactifs)
     * car on doit pouvoir déchiffrer d'anciens codes générés.
     */
    public String decapsulate(String text, UUID dossierId) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // On récupère tous les mots (même inactifs, au cas où l'encapsulation a été faite avant désactivation)
        List<AnonymizationDict> allWords = dictRepository.findAll();
        if (allWords.isEmpty()) {
            return text;
        }

        String decapsulatedText = text;
        int totalRestored = 0;

        for (AnonymizationDict dict : allWords) {
            if (dict.getReplacementCode() != null && !dict.getReplacementCode().isBlank()) {
                String code = dict.getReplacementCode();
                
                // Si le code est présent dans le texte
                if (decapsulatedText.contains(code)) {
                    // Utiliser le remplacement standard
                    String originalWord = dict.getOriginalWord();
                    // On compte approximativement les occurrences
                    int count = decapsulatedText.split(Pattern.quote(code), -1).length - 1;
                    
                    decapsulatedText = decapsulatedText.replace(code, originalWord);
                    totalRestored += count;
                }
            }
        }

        if (dossierId != null && totalRestored > 0) {
            auditTrailService.log(
                    dossierId,
                    "DLP_DECAPSULATION",
                    "system",
                    String.format("Texte décapsulé pour génération doc : %d restaurations effectuées.", totalRestored),
                    null
            );
        }

        return decapsulatedText;
    }
}
