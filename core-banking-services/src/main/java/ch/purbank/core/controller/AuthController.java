package ch.purbank.core.controller;

import ch.purbank.core.dto.AuthenticationRequestDTO;
import ch.purbank.core.dto.AuthenticationResponseDTO;
import ch.purbank.core.dto.ChangePasswordRequestDTO;
import ch.purbank.core.dto.MobileLoginRequestDTO;
import ch.purbank.core.dto.MobileLoginResponseDTO;
import ch.purbank.core.dto.AuthStatusRequestDTO;
import ch.purbank.core.dto.AuthStatusResponseDTO;
import ch.purbank.core.dto.MobileRefreshTokenRequestDTO;
import ch.purbank.core.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, Refresh Token, and Password/Mobile Authorisation Management")
public class AuthController {

    private final AuthenticationService service;

    private String extractIp(String xff, String xr) {
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return xr != null && !xr.isEmpty() ? xr : "Unknown";
    }

    @PostMapping("/login/password")
    @Operation(summary = "Admin/User Login (Email/Password)", description = "Authenticates user/admin with email and password (Legacy flow).")
    public ResponseEntity<AuthenticationResponseDTO> authenticatePassword(
            @RequestBody AuthenticationRequestDTO request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.authenticate(request, httpRequest));
    }

    @PostMapping("/login")
    @Operation(summary = "Mobile/Ebanking Login (Start Authorisation)", description = "Initiates mobile authorisation via contract number and device ID. Returns the mobile-verify code.")
    public ResponseEntity<MobileLoginResponseDTO> mobileLogin(
            @Valid @RequestBody MobileLoginRequestDTO request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
            @RequestHeader(value = "X-Real-IP", required = false) String xr) {

        String ip = extractIp(xff, xr);

        return ResponseEntity.ok(service.mobileLogin(request, ip));
    }

    @PostMapping("/status")
    @Operation(summary = "Authorisation Status Poll", description = "Ebanking/Application polls the server to check the approval status.")
    public ResponseEntity<AuthStatusResponseDTO> getStatus(@RequestBody AuthStatusRequestDTO request) {
        return ResponseEntity.ok(service.checkAuthorisationStatus(request));
    }

    @PostMapping("/refreshtoken")
    @Operation(summary = "Get Refresh Token", description = "Retrieves the Refresh Token after successful authorisation, using mobile-verify code and device ID.")
    public ResponseEntity<AuthenticationResponseDTO> getRefreshToken(
            @Valid @RequestBody MobileRefreshTokenRequestDTO request) {
        return ResponseEntity.ok(service.getRefreshToken(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Token (Legacy/Bearer Token)", description = "Get new Access Token using the Stateful Refresh Token from the Authorization Header.")
    public ResponseEntity<AuthenticationResponseDTO> refreshToken(
            @RequestHeader("Authorization") String authHeader,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.refreshToken(authHeader, httpRequest));
    }

    @PatchMapping("/change-password")
    @Operation(summary = "Change Password (Admin Only)", description = "Authenticated Admin changes their own password.")
    public ResponseEntity<?> changePassword(
            @RequestBody ChangePasswordRequestDTO request,
            Principal connectedUser,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        service.changePassword(request, connectedUser, httpRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelAuthorization(@Valid @RequestBody AuthStatusRequestDTO request) {
        service.cancelAuthorization(request.getMobileVerify(), request.getDeviceId());
        return ResponseEntity.noContent().build();
    }

}