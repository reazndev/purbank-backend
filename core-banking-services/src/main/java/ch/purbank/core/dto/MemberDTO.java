package ch.purbank.core.dto;

import ch.purbank.core.domain.enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class MemberDTO {
    private UUID id;
    private String name; // firstName + lastName
    private String email;
    private MemberRole role;
}