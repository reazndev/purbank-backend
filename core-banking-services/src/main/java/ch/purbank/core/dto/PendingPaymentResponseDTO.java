package ch.purbank.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PendingPaymentResponseDTO {
    private String mobileVerify;
    private String status;  // "PENDING_APPROVAL"
}