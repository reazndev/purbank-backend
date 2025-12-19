package ch.purbank.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthStatusResponseDTO {

    // Either PENDING, APPROVED, REJECTED or INVALID
    private String status;
}