package tn.rihab.authservice.DTO;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSyncDTO implements Serializable {
    // Le "implements Serializable" est OBLIGATOIRE pour que RabbitMQ puisse lire l'objet
    private UUID id;
    private String email;
    private String fullName;
    private String roleCode;
}