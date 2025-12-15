package ch.purbank.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceIDRequestDTO {

    @NotBlank
    @Size(min = 64, max = 64)
    private String deviceId;
}