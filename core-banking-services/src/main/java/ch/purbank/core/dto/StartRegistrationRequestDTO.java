package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartRegistrationRequestDTO {
    @NotBlank
    private String registrationCode;

    @NotBlank
    private String contractNumber; // vertragsnummer

    // Optional: client can send IP, otherwise controller extracts header
    private String ip;
}
