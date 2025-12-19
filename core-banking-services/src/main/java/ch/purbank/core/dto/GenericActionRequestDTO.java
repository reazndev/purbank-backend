package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GenericActionRequestDTO {

    @NotBlank
    @Size(min = 64, max = 64)
    private String deviceId;

    @NotBlank
    private String actionType;

    @NotBlank
    private String actionDataPayload;
}