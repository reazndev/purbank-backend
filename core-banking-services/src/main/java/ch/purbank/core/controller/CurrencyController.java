package ch.purbank.core.controller;

import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.dto.CurrencyRateResponseDTO;
import ch.purbank.core.service.CurrencyConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Currency", description = "Currency conversion and exchange rate endpoints")
public class CurrencyController {

    private final CurrencyConversionService currencyConversionService;

    @GetMapping("/rates")
    @Operation(summary = "Get all conversion rates", description = "Returns all available currency conversion rates")
    public ResponseEntity<CurrencyRateResponseDTO> getAllRates() {
        Map<String, BigDecimal> rates = currencyConversionService.getAllRates();
        return ResponseEntity.ok(new CurrencyRateResponseDTO(rates));
    }

    @GetMapping("/convert")
    @Operation(summary = "Convert amount between currencies", description = "Converts an amount from one currency to another using current exchange rates")
    public ResponseEntity<Map<String, Object>> convertCurrency(
            @Parameter(description = "Amount to convert", required = true, example = "100") @RequestParam BigDecimal amount,
            @Parameter(description = "Source currency", required = true, example = "CHF") @RequestParam Currency from,
            @Parameter(description = "Target currency", required = true, example = "EUR") @RequestParam Currency to) {

        BigDecimal convertedAmount = currencyConversionService.convert(amount, from, to);

        Map<String, Object> response = new HashMap<>();
        response.put("amount", convertedAmount);
        response.put("from", from.name());
        response.put("to", to.name());

        return ResponseEntity.ok(response);
    }

    @GetMapping("")
    @Operation(summary = "Get supported currencies", description = "Returns a list of all supported currency codes")
    public ResponseEntity<List<String>> getSupportedCurrencies() {
        List<String> currencies = Arrays.stream(Currency.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        return ResponseEntity.ok(currencies);
    }
}