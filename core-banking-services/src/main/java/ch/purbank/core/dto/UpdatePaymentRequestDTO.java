package ch.purbank.core.dto;

import ch.purbank.core.domain.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdatePaymentRequestDTO {
    private String toIban;
    private BigDecimal amount;
    private Currency paymentCurrency;
    private String message;
    private String note;
    private LocalDate executionDate;

    @NotBlank(message = "Device ID is required")
    private String deviceId;
}