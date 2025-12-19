package ch.purbank.core.service;

import ch.purbank.core.domain.enums.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CurrencyConversionService {

    private static final Map<String, BigDecimal> CONVERSION_RATES = new HashMap<>();

    static {
        // CHF conversions
        CONVERSION_RATES.put("CHF_EUR", new BigDecimal("1.10"));
        CONVERSION_RATES.put("CHF_USD", new BigDecimal("1.14"));

        // EUR conversions
        CONVERSION_RATES.put("EUR_CHF", new BigDecimal("0.91"));
        CONVERSION_RATES.put("EUR_USD", new BigDecimal("1.04"));

        // USD conversions
        CONVERSION_RATES.put("USD_CHF", new BigDecimal("0.88"));
        CONVERSION_RATES.put("USD_EUR", new BigDecimal("0.96"));
    }

    /**
     * Convert amount from one currency to another
     *
     * @param amount Amount to convert
     * @param from   Source currency
     * @param to     Target currency
     * @return Converted amount
     * @throws IllegalArgumentException if conversion rate not found
     */
    public BigDecimal convert(BigDecimal amount, Currency from, Currency to) {
        if (from == to) {
            return amount;
        }

        BigDecimal rate = getRate(from, to);
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Get conversion rate between two currencies
     *
     * @param from Source currency
     * @param to   Target currency
     * @return Conversion rate
     * @throws IllegalArgumentException if conversion rate not found
     */
    public BigDecimal getRate(Currency from, Currency to) {
        if (from == to) {
            return BigDecimal.ONE;
        }

        String key = from.name() + "_" + to.name();
        BigDecimal rate = CONVERSION_RATES.get(key);

        if (rate == null) {
            throw new IllegalArgumentException(
                    String.format("Conversion rate not available for %s to %s", from, to));
        }

        return rate;
    }

    /**
     * Get all conversion rates
     *
     * @return Map of all conversion rates (key format: "FROM_TO")
     */
    public Map<String, BigDecimal> getAllRates() {
        return new HashMap<>(CONVERSION_RATES);
    }
}