package ch.purbank.core.service;

import ch.purbank.core.domain.RefreshToken;
import ch.purbank.core.domain.User;
import ch.purbank.core.dto.AuthenticationRequestDTO;
import ch.purbank.core.dto.AuthenticationResponseDTO;
import ch.purbank.core.dto.ChangePasswordRequestDTO;
import ch.purbank.core.repository.RefreshTokenRepository;
import ch.purbank.core.repository.UserRepository;
import ch.purbank.core.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository repository;
    private final RefreshTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpirationMs;

    @Value("${application.security.jwt.refresh-token.absolute-expiration}")
    private long absoluteExpirationMs;

    @Transactional
    public AuthenticationResponseDTO authenticate(AuthenticationRequestDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        var user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Invalidate old refresh tokens on new login (Optional, usually good security
        // practice)
        // tokenRepository.deleteByUser(user);

        var jwtToken = jwtService.generateToken(user);
        var refreshToken = createRefreshToken(user);

        return AuthenticationResponseDTO.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    /**
     * Handles the logic for:
     * 1. 20-minute sliding window (updates expiry)
     * 2. 12-hour absolute max age (checks createdAt)
     */
    public AuthenticationResponseDTO refreshToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid Token format");
        }

        final String refreshTokenStr = authHeader.substring(7); // "Bearer " is 7 chars

        RefreshToken refreshToken = tokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        // 1. Check if token is expired based on the 20-minute sliding window
        if (refreshToken.isExpired()) {
            tokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Refresh token expired (Inactivity)");
        }

        // 2. Check absolute expiration (12 hours max life)
        long absoluteMaxAge = refreshToken.getCreatedAt().toEpochMilli() + absoluteExpirationMs;
        if (Instant.now().toEpochMilli() > absoluteMaxAge) {
            tokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Refresh token expired (Max 12h limit reached)");
        }

        User user = refreshToken.getUser();

        // Generate new Access Token
        String accessToken = jwtService.generateToken(user);

        // Slide the window: Update expiry date for another 20 minutes
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshExpirationMs));
        tokenRepository.save(refreshToken);

        return AuthenticationResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    public void changePassword(ChangePasswordRequestDTO request, Principal connectedUser) {
        var user = (User) ((UsernamePasswordAuthenticationToken) connectedUser).getPrincipal();

        // Check if current password is correct
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalStateException("Wrong password");
        }
        // Check if new passwords match
        if (!request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new IllegalStateException("Passwords are not the same");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        repository.save(user);
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs)) // 20 mins from now
                .createdAt(Instant.now())
                .build();
        return tokenRepository.save(refreshToken);
    }
}