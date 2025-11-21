package ch.purbank.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GenericStatusResponse {
    private String status; // OK or FAIL
}
