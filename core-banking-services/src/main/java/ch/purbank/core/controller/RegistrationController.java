package ch.purbank.core.controller;

import ch.purbank.core.dto.*;
import ch.purbank.core.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/mobile/registrations")
@RequiredArgsConstructor
@Slf4j
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/start")
    public ResponseEntity<StartRegistrationResponseDTO> start(
            @Valid @RequestBody StartRegistrationRequestDTO req,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @RequestHeader(value = "X-Real-IP", required = false) String xr) {

        String ip = extractIp(xff, xr);

        String token = registrationService.startRegistration(
                req.getRegistrationCode(),
                req.getContractNumber(),
                ip);

        return ResponseEntity.ok(new StartRegistrationResponseDTO("OK", token));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponseDTO> verify(
            @Valid @RequestBody VerifyRequestDTO req,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @RequestHeader(value = "X-Real-IP", required = false) String xr) {

        String ip = extractIp(xff, xr);

        Optional<String> maybeComplete = registrationService.verifyEmailCode(
                req.getEmailVerifyToken(),
                req.getEmailCode(),
                ip);

        if (maybeComplete.isEmpty()) {
            return ResponseEntity.badRequest().body(new VerifyResponseDTO("FAIL", null));
        }

        return ResponseEntity.ok(new VerifyResponseDTO("OK", maybeComplete.get()));
    }

    @PostMapping("/resend")
    public ResponseEntity<GenericStatusResponse> resend(
            @Valid @RequestBody ResendRequestDTO req,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @RequestHeader(value = "X-Real-IP", required = false) String xr) {

        String ip = extractIp(xff, xr);

        boolean success = registrationService.resendEmailCode(req.getEmailVerifyToken());

        return ResponseEntity.ok(new GenericStatusResponse(success ? "OK" : "FAIL"));
    }

    @PostMapping("/complete")
    public ResponseEntity<GenericStatusResponse> complete(
            @Valid @RequestBody CompleteRegistrationRequestDTO req,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @RequestHeader(value = "X-Real-IP", required = false) String xr) {

        String ip = extractIp(xff, xr);

        boolean success = registrationService.completeRegistration(
                req.getCompleteToken(),
                req.getPublicKey(),
                req.getDeviceName(),
                ip);

        return ResponseEntity.ok(new GenericStatusResponse(success ? "OK" : "FAIL"));
    }

    private String extractIp(String xff, String xr) {
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return xr;
    }
}