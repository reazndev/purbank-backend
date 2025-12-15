package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MobileApprovalRequestDTO {

    @NotBlank
    @Pattern(regexp = "^\\{REQUEST\\}.*|^\\{APPROVE\\}.*|^\\{REJECT\\}.*")
    private String signedMobileVerify;
}