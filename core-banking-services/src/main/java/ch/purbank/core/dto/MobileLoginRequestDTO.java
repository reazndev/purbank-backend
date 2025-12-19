package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MobileLoginRequestDTO {

    @NotBlank(message = "Contract number is required")
    private String contractNumber;

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    private String ip;
}