package ch.purbank.core.controller;

import ch.purbank.core.dto.AuthenticationRequestDTO;
import ch.purbank.core.dto.AuthenticationResponseDTO;
import ch.purbank.core.dto.ChangePasswordRequestDTO;
import ch.purbank.core.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, Refresh Token, and Password Management")
public class AuthController {

    private final AuthenticationService service;

    @PostMapping("/login")
    @Operation(summary = "User/Admin Login", description = "Returns access and refresh tokens")
    public ResponseEntity<AuthenticationResponseDTO> authenticate(@RequestBody AuthenticationRequestDTO request) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Token", description = "Get new access token using refresh token. Sliding window of 20mins.")
    public ResponseEntity<AuthenticationResponseDTO> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(service.refreshToken(authHeader));
    }

    @PatchMapping("/change-password")
    @Operation(summary = "Change Password", description = "Authenticated user/admin changes their own password")
    public ResponseEntity<?> changePassword(
            @RequestBody ChangePasswordRequestDTO request,
            Principal connectedUser) {
        service.changePassword(request, connectedUser);
        return ResponseEntity.ok().build();
    }
}