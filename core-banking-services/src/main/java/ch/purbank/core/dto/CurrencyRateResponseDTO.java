package ch.purbank.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
public class CurrencyRateResponseDTO {
    private Map<String, BigDecimal> rates;
}