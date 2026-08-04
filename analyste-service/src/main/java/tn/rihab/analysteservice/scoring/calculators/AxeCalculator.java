package tn.rihab.analysteservice.scoring.calculators;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.ScoringConfig;

public interface AxeCalculator {
    double calculate(DossierDto dossier, AnalyseDossier analyse, MatchingResult matching, ScoringConfig config);
    double getPoids(ScoringConfig config);
    String getAxeCode();
    String getLabelErreur();
}