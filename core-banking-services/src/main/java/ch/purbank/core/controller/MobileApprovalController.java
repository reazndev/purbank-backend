package ch.purbank.core.controller;

import ch.purbank.core.dto.MobileApprovalRequestDTO;
import ch.purbank.core.service.AuthorisationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/mobile/approve")
public class MobileApprovalController {

    private final AuthorisationService authorisationService;

    public MobileApprovalController(AuthorisationService authorisationService) {
        this.authorisationService = authorisationService;
    }

    @PostMapping("/request")
    public ResponseEntity<String> getApprovalRequestDetails(@Valid @RequestBody MobileApprovalRequestDTO dto) {

        Optional<String> actionDetailsOpt = authorisationService.getActionDetailsForApproval(dto.getSignedMobileVerify());

        if (actionDetailsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired mobile-verify request or signature.");
        }

        return ResponseEntity.ok(actionDetailsOpt.get());
    }

    @PostMapping("/approve")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Void> approveRequest(@Valid @RequestBody MobileApprovalRequestDTO dto) {

        boolean success = authorisationService.approveAuthorisation(dto.getSignedMobileVerify());

        if (!success) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/reject")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Void> rejectRequest(@Valid @RequestBody MobileApprovalRequestDTO dto) {

        boolean success = authorisationService.rejectAuthorisation(dto.getSignedMobileVerify());

        if (!success) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok().build();
    }
}