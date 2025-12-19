package ch.purbank.core.dto;

import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.KontoStatus;
import ch.purbank.core.domain.enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class KontoDetailDTO {
    private UUID kontoId;
    private String kontoName;
    private BigDecimal balance;
    private MemberRole role;
    private BigDecimal zinssatz;
    private String iban;
    private Currency currency;
    private KontoStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
}