package tn.rihab.projectservice.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmailNotificationDTO implements Serializable {
    private String to;
    private String subject;
    private String message;
}
