package ch.purbank.core.dto;

import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class TransactionDTO {
    private UUID transactionId;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private LocalDateTime timestamp;
    private String iban;
    private TransactionType transactionType;
    private Currency currency;
    private String message; // From sender
    private String note; // By owner/manager
}