package ch.purbank.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyResponseDTO {
    private String status; // OK or FAIL
    private String completeToken; // only when OK
}
