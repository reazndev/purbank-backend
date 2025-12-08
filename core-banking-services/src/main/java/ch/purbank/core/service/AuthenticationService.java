package ch.purbank.core.service;

import ch.purbank.core.domain.RefreshToken;
import ch.purbank.core.domain.User;
import ch.purbank.core.dto.*;
import ch.purbank.core.repository.RefreshTokenRepository;
import ch.purbank.core.repository.UserRepository;
import ch.purbank.core.security.JwtService;
import ch.purbank.core.domain.AuthorisationRequest;
import ch.purbank.core.domain.enums.AuthorisationStatus;
import ch.purbank.core.repository.AuthorisationRequestRepository;
import ch.purbank.core.security.SecureTokenGenerator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository repository;
    private final RefreshTokenRepository tokenRepository;
    private final AuthorisationRequestRepository authorisationRepository;
    private final AuthorisationRequestRepository authorisationRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpirationMs;

    @Value("${application.security.jwt.refresh-token.absolute-expiration}")
    private long absoluteExpirationMs;

    private static final int MOBILE_VERIFY_TOKEN_LENGTH = 64;

    private String logToken(String token) {
        return token != null && token.length() > 8 ? token.substring(0, 4) + "..." : "null";
    }

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

    @Transactional
    public MobileLoginResponseDTO mobileLogin(MobileLoginRequestDTO request, String ipAddress) {
        log.info("mobileLogin initiated for contract={} from ip={}", request.getContractNumber(), ipAddress);

        var user = repository.findByContractNumber(request.getContractNumber())
                .orElseThrow(() -> new IllegalArgumentException("Invalid contract number or device ID"));

        authorisationRepository.findByUserIdAndStatus(user.getId(), AuthorisationStatus.PENDING)
                .ifPresent(oldRequest -> {
                    oldRequest.markCompleted(AuthorisationStatus.INVALID);
                    authorisationRepository.save(oldRequest);
                    log.warn("Invalidated old PENDING authorisation request for user {}", user.getId());
                });

        String mobileVerifyCode = SecureTokenGenerator.generateToken(MOBILE_VERIFY_TOKEN_LENGTH);

        AuthorisationRequest authRequest = AuthorisationRequest.builder()
                .user(user)
                .mobileVerifyCode(mobileVerifyCode)
                .deviceId(request.getDeviceId())
                .actionType("LOGIN")
                .ipAddress(ipAddress)
                .actionPayload("{\"ip-address\": \"" + ipAddress + "\", \"ip-location\": \"Pending geolocation\"}")
                .status(AuthorisationStatus.PENDING)
                .build();

        authorisationRepository.save(authRequest);

        log.info("Mobile login request created for user={} with mobileVerifyCode={}", user.getId(),
                logToken(mobileVerifyCode));

        return new MobileLoginResponseDTO(mobileVerifyCode, "PENDING");
    }

    public AuthStatusResponseDTO checkAuthorisationStatus(AuthStatusRequestDTO request) {
        AuthorisationRequest authRequest = authorisationRepository.findByMobileVerifyCode(request.getMobileVerify())
                .orElseThrow(() -> new IllegalArgumentException("Invalid mobile-verify code or request not found."));

        if (!authRequest.getDeviceId().equals(request.getDeviceId())) {
            throw new IllegalArgumentException("Device ID mismatch.");
        }

        if (authRequest.isExpired() && authRequest.getStatus() == AuthorisationStatus.PENDING) {
            authRequest.markCompleted(AuthorisationStatus.INVALID);
            authorisationRepository.save(authRequest);
            return new AuthStatusResponseDTO(AuthorisationStatus.INVALID.name());
        }

        return new AuthStatusResponseDTO(authRequest.getStatus().name());
    }

    @Transactional
    public AuthenticationResponseDTO getRefreshToken(MobileRefreshTokenRequestDTO request) {
        AuthorisationRequest authRequest = authorisationRepository.findByMobileVerifyCode(request.getMobileVerify())
                .orElseThrow(() -> new IllegalArgumentException("Invalid mobile-verify code."));

        if (!authRequest.getDeviceId().equals(request.getDeviceId())) {
            throw new IllegalArgumentException("Device ID mismatch.");
        }
        if (authRequest.getStatus() != AuthorisationStatus.APPROVED) {
            log.warn("Attempt to retrieve token with non-approved status: {}", authRequest.getStatus());
            throw new IllegalStateException("Authorisation not approved.");
        }

        User user = authRequest.getUser();

        if (authRequest.getCompletedAt() != null
                && authRequest.getCompletedAt().isBefore(LocalDateTime.now().minusSeconds(5))) {
            log.error("Token already issued or authorisation completed too long ago. Request completed at: {}",
                    authRequest.getCompletedAt());
            throw new IllegalStateException("Token already issued or authorisation expired.");
        }

        authRequest.markCompleted(AuthorisationStatus.INVALID);
        authorisationRepository.save(authRequest);

        tokenRepository.deleteByUser(user);

        var jwtToken = jwtService.generateToken(user);
        var refreshToken = createRefreshToken(user);

        log.info("Successfully issued tokens for user {} via mobile verification.", user.getId());

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

        long inactivityAge = refreshToken.getExpiryDate().toEpochMilli();
        if (Instant.now().toEpochMilli() > inactivityAge) {
            tokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Invalid refresh token or token expired.");
        }

        long absoluteMaxAge = refreshToken.getCreatedAt().toEpochMilli() + absoluteExpirationMs;
        if (Instant.now().toEpochMilli() > absoluteMaxAge) {
            tokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Invalid refresh token or token expired.");
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

    public void cancelAuthorization(String mobileVerify, String deviceId) {
        AuthorisationRequest request = authorisationRequestRepository
                .findByMobileVerifyCodeAndDeviceIdAndStatus(
                        mobileVerify,
                        deviceId,
                        AuthorisationStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Keine ausstehende Autorisierungsanfrage gefunden oder bereits abgeschlossen."));

        request.setStatus(AuthorisationStatus.CANCELLED);
        authorisationRequestRepository.save(request);
    }
}