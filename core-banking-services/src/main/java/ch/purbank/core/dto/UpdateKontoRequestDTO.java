package ch.purbank.core.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateKontoRequestDTO {
    private String name;
    private BigDecimal zinssatz;
}