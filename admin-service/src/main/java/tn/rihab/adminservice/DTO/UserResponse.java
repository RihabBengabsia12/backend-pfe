package tn.rihab.adminservice.DTO;

import lombok.*;
import tn.rihab.adminservice.entity.AppUser;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID        id;
    private String      email;
    private String      fullName;
    private String      status;
    private String      role; // Ce champ peut maintenant être modifié par le Setter
    private Set<String> permissions;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static UserResponse from(AppUser u) {
        if (u == null) return null;
        return UserResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .status(u.getStatus())
                .role(u.getRoleCode())
                .permissions(u.getPermissionCodes())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}