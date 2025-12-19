package ch.purbank.core.service;

import ch.purbank.core.domain.AuthorisationRequest;
import ch.purbank.core.domain.PendingPayment;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.AuthorisationStatus;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import ch.purbank.core.repository.AuthorisationRequestRepository;
import ch.purbank.core.repository.PendingPaymentRepository;
import ch.purbank.core.security.SecureTokenGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AuthorisationService {

    private static final int MOBILE_VERIFY_TOKEN_LENGTH = 64;

    private final AuthorisationRequestRepository authorisationRequestRepository;
    private final PendingPaymentRepository pendingPaymentRepository;
    private final MobileSecurityService mobileSecurityService;
    private final ActionExecutionService actionExecutionService;
    private final ObjectMapper objectMapper;

    public AuthorisationService(AuthorisationRequestRepository authorisationRequestRepository,
                                PendingPaymentRepository pendingPaymentRepository,
                                MobileSecurityService mobileSecurityService,
                                ActionExecutionService actionExecutionService,
                                ObjectMapper objectMapper) {
        this.authorisationRequestRepository = authorisationRequestRepository;
        this.pendingPaymentRepository = pendingPaymentRepository;
        this.mobileSecurityService = mobileSecurityService;
        this.actionExecutionService = actionExecutionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String createAuthorisationRequest(User user, String deviceId, String ipAddress, String actionType,
                                             String actionPayload) {

        authorisationRequestRepository.findByUserAndStatus(user, AuthorisationStatus.PENDING)
                .forEach(req -> {
                    req.setStatus(AuthorisationStatus.INVALID);
                    authorisationRequestRepository.save(req);
                });

        String mobileVerifyCode = SecureTokenGenerator.generateToken(MOBILE_VERIFY_TOKEN_LENGTH);

        AuthorisationRequest newRequest = AuthorisationRequest.builder()
                .user(user)
                .mobileVerifyCode(mobileVerifyCode)
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .actionType(actionType)
                .actionPayload(actionPayload)
                .build();

        authorisationRequestRepository.save(newRequest);

        return mobileVerifyCode;
    }

    public Optional<String> getActionDetailsForApproval(String signedMobileVerify) {
        String mobileVerifyCode = mobileSecurityService.extractMobileVerifyCode(signedMobileVerify);

        if (!signedMobileVerify.startsWith("{REQUEST}")) {
            return Optional.empty();
        }

        // First check pending payments
        Optional<PendingPayment> pendingPaymentOpt = pendingPaymentRepository.findByMobileVerifyCodeAndStatus(
                mobileVerifyCode, PendingPaymentStatus.PENDING);

        if (pendingPaymentOpt.isPresent()) {
            PendingPayment pendingPayment = pendingPaymentOpt.get();

            if (pendingPayment.isExpired()) {
                return Optional.empty();
            }

            if (!mobileSecurityService.isValidSignature(pendingPayment.getUser(), signedMobileVerify)) {
                return Optional.empty();
            }

            return Optional.of(buildPaymentActionPayload(pendingPayment));
        }

        // Fall back to checking authorisation requests (for other action types)
        Optional<AuthorisationRequest> requestOpt = authorisationRequestRepository.findByMobileVerifyCodeAndStatus(
                mobileVerifyCode, AuthorisationStatus.PENDING);

        if (requestOpt.isEmpty()) {
            return Optional.empty();
        }

        AuthorisationRequest request = requestOpt.get();

        if (request.isExpired()) {
            return Optional.empty();
        }

        if (!mobileSecurityService.isValidSignature(request.getUser(), signedMobileVerify)) {
            return Optional.empty();
        }

        return Optional.of(request.getActionPayload());
    }

    private String buildPaymentActionPayload(PendingPayment pendingPayment) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "PAYMENT");
            payload.put("amount", pendingPayment.getAmount().toString());
            payload.put("to", pendingPayment.getToIban());
            payload.put("message", pendingPayment.getMessage());
            payload.put("executionType", pendingPayment.getExecutionType().toString());
            payload.put("executionDate", pendingPayment.getExecutionDate().toString());

            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Error building payment action payload", e);
            return "{}";
        }
    }

    @Transactional
    public boolean approveAuthorisation(String signedMobileVerify) {
        return handleMobileApproval(signedMobileVerify, "{APPROVE}", AuthorisationStatus.APPROVED);
    }

    @Transactional
    public boolean rejectAuthorisation(String signedMobileVerify) {
        return handleMobileApproval(signedMobileVerify, "{REJECT}", AuthorisationStatus.REJECTED);
    }

    private boolean handleMobileApproval(String signedMobileVerify, String requiredPrefix,
                                         AuthorisationStatus newStatus) {
        String mobileVerifyCode = mobileSecurityService.extractMobileVerifyCode(signedMobileVerify);

        if (!signedMobileVerify.startsWith(requiredPrefix)) {
            return false;
        }

        // First check if this is a pending payment
        Optional<PendingPayment> pendingPaymentOpt = pendingPaymentRepository.findByMobileVerifyCode(mobileVerifyCode);

        if (pendingPaymentOpt.isPresent()) {
            PendingPayment pendingPayment = pendingPaymentOpt.get();

            if (pendingPayment.getStatus() != PendingPaymentStatus.PENDING || pendingPayment.isExpired()) {
                return false;
            }

            if (!mobileSecurityService.isValidSignature(pendingPayment.getUser(), signedMobileVerify)) {
                return false;
            }

            if (newStatus == AuthorisationStatus.APPROVED) {
                AuthorisationRequest tempRequest = AuthorisationRequest.builder()
                        .mobileVerifyCode(mobileVerifyCode)
                        .actionType("PAYMENT")
                        .build();

                actionExecutionService.executeAction(tempRequest);
            } else {
                // Rejection
                pendingPayment.markCompleted(PendingPaymentStatus.REJECTED);
                pendingPaymentRepository.save(pendingPayment);
            }

            return true;
        }

        // Fall back to checking authorisation requests (for other action types)
        Optional<AuthorisationRequest> requestOpt = authorisationRequestRepository
                .findByMobileVerifyCode(mobileVerifyCode);

        if (requestOpt.isEmpty()) {
            return false;
        }

        AuthorisationRequest request = requestOpt.get();

        if (request.getStatus() != AuthorisationStatus.PENDING || request.isExpired()) {
            return false;
        }

        if (!mobileSecurityService.isValidSignature(request.getUser(), signedMobileVerify)) {
            return false;
        }

        request.markCompleted(newStatus);
        authorisationRequestRepository.save(request);

        if (newStatus == AuthorisationStatus.APPROVED) {
            actionExecutionService.executeAction(request);
        }

        return true;
    }
}