package ch.purbank.core.dto;

import ch.purbank.core.domain.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKontoRequestDTO {
    @NotBlank
    private String name;

    private Currency currency; // Optional, defaults to CHF
}