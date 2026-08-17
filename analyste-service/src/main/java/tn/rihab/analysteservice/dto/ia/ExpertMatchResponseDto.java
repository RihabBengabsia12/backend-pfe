package tn.rihab.analysteservice.dto.ia;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpertMatchResponseDto {
    private String expertId;
    private double score;
}
