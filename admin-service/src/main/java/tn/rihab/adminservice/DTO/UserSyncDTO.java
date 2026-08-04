package tn.rihab.adminservice.DTO;

import java.io.Serializable;
import java.util.UUID; // <--- N'oublie pas l'import
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder // Pratique pour tes tests unitaires
public class UserSyncDTO implements Serializable {

    private UUID id; // <--- C'est ce champ qui manquait pour le "getId()" !
    private String email;
    private String fullName;
    private String role;
}