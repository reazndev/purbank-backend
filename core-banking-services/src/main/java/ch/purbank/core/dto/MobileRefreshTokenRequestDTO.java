package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MobileRefreshTokenRequestDTO {

    @NotBlank(message = "Mobile verify code is required")
    private String mobileVerify;

    @NotBlank(message = "Device ID is required")
    private String deviceId;
}