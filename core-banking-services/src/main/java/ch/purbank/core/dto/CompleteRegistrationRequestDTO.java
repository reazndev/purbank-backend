package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompleteRegistrationRequestDTO {
    @NotBlank
    private String completeToken;

    @NotBlank
    private String publicKey; // PEM format

    @NotBlank
    private String deviceName;

    private String ip;
}
