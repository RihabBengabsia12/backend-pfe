package tn.rihab.adminservice.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "full_name obligatoire")
    private String fullName;

    @Email(message = "Email invalide")
    @NotBlank(message = "Email obligatoire")
    private String email;

    @NotBlank(message = "Rôle obligatoire")
    private String roleCode;   // ADMIN, ANALYST, MANAGER
}