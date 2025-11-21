package ch.purbank.core.controller;

import ch.purbank.core.dto.*;
import ch.purbank.core.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Mobile - Registration", description = "Mobile app registration flow endpoints")
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/start")
    @Operation(summary = "Start registration", description = "Initiates the registration process by validating the registration code and sending a verification email", responses = {
            @ApiResponse(responseCode = "200", description = "Registration started successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid registration code or contract number")
    })
    public ResponseEntity<StartRegistrationResponseDTO> start(
            @Parameter(description = "Registration request with code and contract number", required = true) @Valid @RequestBody StartRegistrationRequestDTO req,
            @Parameter(description = "X-Forwarded-For header (client IP)", required = false) @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @Parameter(description = "X-Real-IP header (client IP)", required = false) @RequestHeader(value = "X-Real-IP", required = false) String xr) {

        String ip = extractIp(xff, xr);

        String token = registrationService.startRegistration(
                req.getRegistrationCode(),
                req.getContractNumber(),
                ip);

        return ResponseEntity.ok(new StartRegistrationResponseDTO("OK", token));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify email code", description = "Verifies the email code sent to the user and returns a completion token", responses = {
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired verification code")
    })
    public ResponseEntity<VerifyResponseDTO> verify(
            @Parameter(description = "Email verification request", required = true) @Valid @RequestBody VerifyRequestDTO req,
            @Parameter(description = "X-Forwarded-For header (client IP)", required = false) @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @Parameter(description = "X-Real-IP header (client IP)", required = false) @RequestHeader(value = "X-Real-IP", required = false) String xr) {

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
    @Operation(summary = "Resend verification code", description = "Resends the email verification code (limited to 1 resend per session)", responses = {
            @ApiResponse(responseCode = "200", description = "Code resent successfully"),
            @ApiResponse(responseCode = "400", description = "Resend limit reached or invalid token")
    })
    public ResponseEntity<GenericStatusResponse> resend(
            @Parameter(description = "Resend request with email verify token", required = true) @Valid @RequestBody ResendRequestDTO req,
            @Parameter(description = "X-Forwarded-For header (client IP)", required = false) @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @Parameter(description = "X-Real-IP header (client IP)", required = false) @RequestHeader(value = "X-Real-IP", required = false) String xr) {

        String ip = extractIp(xff, xr);

        boolean success = registrationService.resendEmailCode(req.getEmailVerifyToken());

        return ResponseEntity.ok(new GenericStatusResponse(success ? "OK" : "FAIL"));
    }

    @PostMapping("/complete")
    @Operation(summary = "Complete registration", description = "Completes the registration by registering the mobile device with its public key", responses = {
            @ApiResponse(responseCode = "200", description = "Registration completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid token or public key already registered")
    })
    public ResponseEntity<GenericStatusResponse> complete(
            @Parameter(description = "Complete registration request with device details", required = true, content = @Content(examples = @ExampleObject(name = "Example Request", value = "{\n"
                    +
                    "  \"completeToken\": \"aIlwo2rPn7CAh8rhGWLH8kNblYxvbMWUp7TApABcPYm54ODu7FrzCe14VrqvfjQg\",\n" +
                    "  \"publicKey\": \"-----BEGIN PUBLIC KEY-----\\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\\n-----END PUBLIC KEY-----\\n\",\n"
                    +
                    "  \"deviceName\": \"iPhone 15 Pro\",\n" +
                    "  \"ip\": \"192.168.1.100\"\n" +
                    "}"))) @Valid @RequestBody CompleteRegistrationRequestDTO req,
            @Parameter(description = "X-Forwarded-For header (client IP)", required = false) @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @Parameter(description = "X-Real-IP header (client IP)", required = false) @RequestHeader(value = "X-Real-IP", required = false) String xr) {

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