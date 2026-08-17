package tn.rihab.analysteservice.dto.ia;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpertMatchRequestDto {
    private String roleRequis;
    private List<ExpertInfo> expertsDisponibles;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpertInfo {
        private String id;
        private String nom;
        private List<String> specialites;
    }
}
