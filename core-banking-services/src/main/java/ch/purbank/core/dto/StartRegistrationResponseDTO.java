package ch.purbank.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StartRegistrationResponseDTO {
    private String status; // "OK"
    private String emailVerifyToken;
}
