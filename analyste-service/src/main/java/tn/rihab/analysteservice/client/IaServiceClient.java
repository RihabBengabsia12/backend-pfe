package tn.rihab.analysteservice.client;

import tn.rihab.analysteservice.config.FeignConfig;
import tn.rihab.analysteservice.dto.ia.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(
        name = "ia-service",
        url  = "${IA_SERVICE_URL:http://localhost:8000}",
        configuration = FeignConfig.class
)
public interface IaServiceClient {

    @PostMapping("/extract/phase2")
    ExtractionResponseDto extractPhase2(@RequestBody ExtractionRequestDto request);

    @PostMapping("/extract/risks")
    RiskAnalysisResponseDto extractRisks(@RequestBody ExtractionRequestDto request);

    @PostMapping("/matching/extract-requirements")
    RequirementsResponseDto extractRequirements(@RequestBody ExtractionRequestDto request);

    @PostMapping("/matching/generate-matrix")
    MatrixResponseDto generateMatrix(@RequestBody MatchingContextDto context);

    @PostMapping("/generate/apo-texts")
    ApoTextsResponseDto generateApoTexts(@RequestBody ApoGenerationContextDto context);

    @PostMapping("/generate/methodologie")
    MethodologieResponseDto generateMethodologie(@RequestBody ApoGenerationContextDto context);

    @PostMapping("/generate/checklist")
    ChecklistResponseDto generateChecklist(@RequestBody ChecklistRequestDto request);

    // CORRECTION : URL corrigée de /generate/nogo-report → /scoring/nogo-report
    @PostMapping("/scoring/nogo-report")
    NoGoReportResponseDto generateNogoReport(@RequestBody NoGoContextDto context);

    @PostMapping("/generate/audit-report")
    AuditReportResponseDto generateAuditReport(@RequestBody AuditContextDto context);
}