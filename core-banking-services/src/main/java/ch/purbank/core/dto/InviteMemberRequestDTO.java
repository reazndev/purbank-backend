package ch.purbank.core.dto;

import ch.purbank.core.domain.enums.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteMemberRequestDTO {
    @NotBlank
    private String contractNumber; // Vertragsnummer of user to invite

    @NotNull
    private MemberRole role;

    @NotBlank(message = "Device ID is required")
    private String deviceId;
}