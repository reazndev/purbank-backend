package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartRegistrationRequestDTO {
    @NotBlank
    private String registrationCode;

    @NotBlank
    private String contractNumber; // vertragsnummer
}
