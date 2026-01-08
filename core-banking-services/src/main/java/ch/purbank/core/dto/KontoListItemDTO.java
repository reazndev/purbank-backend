package ch.purbank.core.dto;

import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
public class KontoListItemDTO {
    private UUID kontoId;
    private String kontoName;
    private BigDecimal balance;
    private MemberRole role;
    private String iban;
    private Currency currency;
}