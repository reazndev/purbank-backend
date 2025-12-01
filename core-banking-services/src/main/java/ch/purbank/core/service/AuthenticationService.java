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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

        var user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } else if (user.getPassword() == null || user.getPassword().isEmpty()) {
            log.info("Passwordless user logged in via external validation: {}", user.getEmail());
        } else {
            throw new IllegalStateException("Authentication failed: Missing credentials for user role.");
        }

        var jwtToken = jwtService.generateToken(user);

        tokenRepository.deleteByUser(user);

        var refreshToken = createRefreshToken(user);

        return AuthenticationResponseDTO.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    public AuthenticationResponseDTO refreshToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid Token format");
        }

        final String refreshTokenStr = authHeader.substring(7);

        RefreshToken refreshToken = tokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (refreshToken.isExpired()) {
            tokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Refresh token expired (Inactivity)");
        }

        long absoluteMaxAge = refreshToken.getCreatedAt().toEpochMilli() + absoluteExpirationMs;
        if (Instant.now().toEpochMilli() > absoluteMaxAge) {
            tokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Refresh token expired (Max 12h limit reached)");
        }

        User user = refreshToken.getUser();

        String accessToken = jwtService.generateToken(user);

        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshExpirationMs));
        tokenRepository.save(refreshToken);

        return AuthenticationResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    public void changePassword(ChangePasswordRequestDTO request, Principal connectedUser) {
        var user = (User) ((UsernamePasswordAuthenticationToken) connectedUser).getPrincipal();

        if (!user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new IllegalStateException("Access Denied: Only Administrators are allowed to change passwords.");
        }

        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new IllegalStateException("Administrator must have a current password set to change it.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalStateException("Wrong password");
        }
        if (!request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new IllegalStateException("Passwords are not the same");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        repository.save(user);
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .createdAt(Instant.now())
                .build();
        return tokenRepository.save(refreshToken);
    }
}
