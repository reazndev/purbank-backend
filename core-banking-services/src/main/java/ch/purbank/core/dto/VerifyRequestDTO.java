package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyRequestDTO {
    @NotBlank
    private String emailVerifyToken;

    @NotBlank
    private String emailCode;

    private String ip;
}
