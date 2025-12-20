package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteKontoRequestDTO {
    @NotBlank(message = "Device ID is required")
    private String deviceId;
}