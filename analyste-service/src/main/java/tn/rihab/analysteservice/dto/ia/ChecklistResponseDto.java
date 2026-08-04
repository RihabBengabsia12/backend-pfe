package tn.rihab.analysteservice.dto.ia;



import lombok.*;
import java.util.List;

/** Pièces pointées générées par ia-service selon les règles du bailleur. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChecklistResponseDto {
    private List<PieceDto> pieces;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PieceDto {
        private String nom;
        private String description;
        private Boolean obligatoire;
        private String source; // "BM-SBD" | "AFD-CPAR" | "UE-PRAG" | "STANDARD"
    }
}