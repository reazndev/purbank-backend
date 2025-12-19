package ch.purbank.core.controller;

import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.dto.CurrencyRateResponseDTO;
import ch.purbank.core.service.CurrencyConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/currencies")
@RequiredArgsConstructor
@Slf4j
public class CurrencyController {

    private final CurrencyConversionService currencyConversionService;

    /**
     * Get all conversion rates
     * GET /api/v1/currencies/rates
     */
    @GetMapping("/rates")
    public ResponseEntity<CurrencyRateResponseDTO> getAllRates() {
        Map<String, BigDecimal> rates = currencyConversionService.getAllRates();
        return ResponseEntity.ok(new CurrencyRateResponseDTO(rates));
    }

    /**
     * Convert amount between currencies
     * GET /api/v1/currencies/convert?amount=100&from=CHF&to=EUR
     */
    @GetMapping("/convert")
    public ResponseEntity<Map<String, Object>> convertCurrency(
            @RequestParam BigDecimal amount,
            @RequestParam Currency from,
            @RequestParam Currency to) {

        BigDecimal convertedAmount = currencyConversionService.convert(amount, from, to);

        Map<String, Object> response = new HashMap<>();
        response.put("amount", convertedAmount);
        response.put("from", from.name());
        response.put("to", to.name());

        return ResponseEntity.ok(response);
    }

    /**
     * Get list of supported currencies
     * GET /api/v1/currencies
     */
    @GetMapping("")
    public ResponseEntity<List<String>> getSupportedCurrencies() {
        List<String> currencies = Arrays.stream(Currency.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        return ResponseEntity.ok(currencies);
    }
}