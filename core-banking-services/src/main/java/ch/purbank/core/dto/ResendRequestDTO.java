package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendRequestDTO {
    @NotBlank
    private String emailVerifyToken;
    private String ip;
}
