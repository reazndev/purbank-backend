package ch.purbank.core.dto;

import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.PaymentExecutionType;
import ch.purbank.core.domain.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
public class PaymentDTO {
    private UUID id;
    private UUID kontoId;
    private String toIban;
    private BigDecimal amount;
    private Currency paymentCurrency;
    private String message;
    private String note;
    private PaymentExecutionType executionType;
    private LocalDate executionDate;
    private PaymentStatus status;
    private boolean locked;
}