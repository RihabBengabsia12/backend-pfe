package tn.rihab.adminservice.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailNotificationDTO implements Serializable {
    private String to;
    private String subject;
    private String message;
}