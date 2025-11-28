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
    @Operation(summary = "Admin/User Login (Get Bearer Token)", description = "Authenticates user/admin and returns the Access Token (Bearer Token) and the Stateful Refresh Token.")
    public ResponseEntity<AuthenticationResponseDTO> authenticate(@RequestBody AuthenticationRequestDTO request) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Token", description = "Get new Access Token using the Stateful Refresh Token. Enforces a 20-minute sliding window and 12-hour absolute max age.")
    public ResponseEntity<AuthenticationResponseDTO> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(service.refreshToken(authHeader));
    }

    @PatchMapping("/change-password")
    @Operation(summary = "Change Password (Admin Only)", description = "Authenticated Admin changes their own password. This endpoint is restricted by SecurityConfig to users with ROLE_ADMIN.")
    public ResponseEntity<?> changePassword(
            @RequestBody ChangePasswordRequestDTO request,
            Principal connectedUser) {
        service.changePassword(request, connectedUser);
        return ResponseEntity.ok().build();
    }
}
