package ch.purbank.core.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdatePaymentRequestDTO {
    private String toIban;
    private BigDecimal amount;
    private String message;
    private String note;
    private LocalDate executionDate;
}