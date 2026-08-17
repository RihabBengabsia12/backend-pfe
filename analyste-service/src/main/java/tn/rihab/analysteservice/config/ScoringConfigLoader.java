package tn.rihab.analysteservice.config;

import tn.rihab.analysteservice.model.ScoringConfig;
import tn.rihab.analysteservice.repository.ScoringConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class ScoringConfigLoader {

    private final ScoringConfigRepository repo;

    @EventListener(ApplicationReadyEvent.class)
    public void initDefaultConfig() {
        if (repo.count() == 0) {
            ScoringConfig defaultConfig = ScoringConfig.builder()
                    // Seuils de décision P-Win
                    .seuilNoGo(20.0)
                    .seuilGoConditionnel(40.0)
                    .seuilGoFort(70.0)
                    // Pondérations des 5 axes (doivent sommer à 1.0)
                    .poidsA_faisabilite(0.25)
                    .poidsB_rentabilite(0.25)
                    .poidsC_risques(0.25)
                    .poidsD_concurrence(0.15)
                    .poidsE_conformite(0.10)
                    .seuilCompatCompetences(0.50)
                    .seuilCompatExperts(0.40)
                    .seuilAlignementOui(0.80)
                    .seuilAlignementPartiel(0.50)
                    // TJM (en euros / jour)
                    .tjmMinEgis(350.0)
                    .tjmMaxEgis(3000.0)
                    .build();

            repo.save(defaultConfig);
            log.info("[ScoringConfig] Configuration par défaut initialisée : "
                    + "seuilNoGo=20%, seuilGo=40%, seuilGoFort=70%, "
                    + "poids A/B/C/D/E = 25/25/25/15/10, TJM=[350,3000]€");
        } else {
            log.info("[ScoringConfig] Configuration existante chargée depuis la base");
        }
    }
}
