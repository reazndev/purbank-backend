package ch.purbank.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MobileLoginResponseDTO {
    private String mobileVerifyCode;
    private String status;
}