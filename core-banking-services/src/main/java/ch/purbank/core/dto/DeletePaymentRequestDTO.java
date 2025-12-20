package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeletePaymentRequestDTO {
    @NotBlank(message = "Device ID is required")
    private String deviceId;
}