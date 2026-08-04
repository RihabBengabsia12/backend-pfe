package tn.rihab.authservice.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor  // <--- AJOUTÉ
@AllArgsConstructor // <--- AJOUTÉ
public class ChangePasswordRequest {

    @NotBlank(message = "Ancien mot de passe obligatoire")
    private String oldPassword;

    @NotBlank(message = "Nouveau mot de passe obligatoire")
    @Size(min = 6, message = "Minimum 6 caractères")
    private String newPassword;
}