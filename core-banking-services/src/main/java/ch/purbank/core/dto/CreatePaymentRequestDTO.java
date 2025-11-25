package ch.purbank.core.dto;

import ch.purbank.core.domain.enums.PaymentExecutionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreatePaymentRequestDTO {
    @NotNull
    private UUID kontoId;

    @NotBlank
    private String toIban;

    @NotNull
    private BigDecimal amount;

    private String message;

    private String note;

    @NotNull
    private PaymentExecutionType executionType;

    // Required if executionType is NORMAL, ignored if INSTANT
    private LocalDate executionDate;
}