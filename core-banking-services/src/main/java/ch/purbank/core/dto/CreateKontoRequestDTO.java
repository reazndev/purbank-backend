package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKontoRequestDTO {
    @NotBlank
    private String name;
}