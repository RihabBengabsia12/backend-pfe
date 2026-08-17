package tn.rihab.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import tn.rihab.projectservice.dto.NoGoDecisionRequestDto;

import java.util.UUID;

@FeignClient(name = "analyste-service")
public interface AnalysteServiceClient {

    @PostMapping("/api/scoring/{id}/process-decision")
    void processDecision(@PathVariable("id") UUID id, @RequestBody NoGoDecisionRequestDto request);

    @PostMapping("/api/scoring/{id}/generate-audit")
    void generateAudit(@PathVariable("id") UUID id);

    @org.springframework.web.bind.annotation.GetMapping("/api/matching/{id}/result")
    tn.rihab.projectservice.dto.MatchingResultDto getMatchingResult(@PathVariable("id") UUID id);
}
