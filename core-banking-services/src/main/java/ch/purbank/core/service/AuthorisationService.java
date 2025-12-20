package ch.purbank.core.service;

import ch.purbank.core.domain.*;
import ch.purbank.core.domain.enums.AuthorisationStatus;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import ch.purbank.core.repository.*;
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
    private final PendingPaymentUpdateRepository pendingPaymentUpdateRepository;
    private final PendingPaymentDeleteRepository pendingPaymentDeleteRepository;
    private final PendingKontoDeleteRepository pendingKontoDeleteRepository;
    private final PendingMemberInviteRepository pendingMemberInviteRepository;
    private final MobileSecurityService mobileSecurityService;
    private final ActionExecutionService actionExecutionService;
    private final ObjectMapper objectMapper;

    public AuthorisationService(AuthorisationRequestRepository authorisationRequestRepository,
                                PendingPaymentRepository pendingPaymentRepository,
                                PendingPaymentUpdateRepository pendingPaymentUpdateRepository,
                                PendingPaymentDeleteRepository pendingPaymentDeleteRepository,
                                PendingKontoDeleteRepository pendingKontoDeleteRepository,
                                PendingMemberInviteRepository pendingMemberInviteRepository,
                                MobileSecurityService mobileSecurityService,
                                ActionExecutionService actionExecutionService,
                                ObjectMapper objectMapper) {
        this.authorisationRequestRepository = authorisationRequestRepository;
        this.pendingPaymentRepository = pendingPaymentRepository;
        this.pendingPaymentUpdateRepository = pendingPaymentUpdateRepository;
        this.pendingPaymentDeleteRepository = pendingPaymentDeleteRepository;
        this.pendingKontoDeleteRepository = pendingKontoDeleteRepository;
        this.pendingMemberInviteRepository = pendingMemberInviteRepository;
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
            payload.put("note", pendingPayment.getNote());
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

    private boolean handlePendingApproval(Object pending, User user, String signedMobileVerify,
                                          AuthorisationStatus newStatus, String actionType,
                                          String mobileVerifyCode) {
        // Check status and expiration
        boolean isExpired = false;
        PendingPaymentStatus status = null;

        if (pending instanceof PendingPayment) {
            PendingPayment p = (PendingPayment) pending;
            status = p.getStatus();
            isExpired = p.isExpired();
        } else if (pending instanceof PendingPaymentUpdate) {
            PendingPaymentUpdate p = (PendingPaymentUpdate) pending;
            status = p.getStatus();
            isExpired = p.isExpired();
        } else if (pending instanceof PendingPaymentDelete) {
            PendingPaymentDelete p = (PendingPaymentDelete) pending;
            status = p.getStatus();
            isExpired = p.isExpired();
        } else if (pending instanceof PendingKontoDelete) {
            PendingKontoDelete p = (PendingKontoDelete) pending;
            status = p.getStatus();
            isExpired = p.isExpired();
        } else if (pending instanceof PendingMemberInvite) {
            PendingMemberInvite p = (PendingMemberInvite) pending;
            status = p.getStatus();
            isExpired = p.isExpired();
        }

        if (status != PendingPaymentStatus.PENDING || isExpired) {
            return false;
        }

        if (!mobileSecurityService.isValidSignature(user, signedMobileVerify)) {
            return false;
        }

        if (newStatus == AuthorisationStatus.APPROVED) {
            AuthorisationRequest tempRequest = AuthorisationRequest.builder()
                    .mobileVerifyCode(mobileVerifyCode)
                    .actionType(actionType)
                    .build();

            actionExecutionService.executeAction(tempRequest);
        } else {
            // Rejection - mark as rejected and save
            if (pending instanceof PendingPayment) {
                PendingPayment p = (PendingPayment) pending;
                p.markCompleted(PendingPaymentStatus.REJECTED);
                pendingPaymentRepository.save(p);
            } else if (pending instanceof PendingPaymentUpdate) {
                PendingPaymentUpdate p = (PendingPaymentUpdate) pending;
                p.markCompleted(PendingPaymentStatus.REJECTED);
                pendingPaymentUpdateRepository.save(p);
            } else if (pending instanceof PendingPaymentDelete) {
                PendingPaymentDelete p = (PendingPaymentDelete) pending;
                p.markCompleted(PendingPaymentStatus.REJECTED);
                pendingPaymentDeleteRepository.save(p);
            } else if (pending instanceof PendingKontoDelete) {
                PendingKontoDelete p = (PendingKontoDelete) pending;
                p.markCompleted(PendingPaymentStatus.REJECTED);
                pendingKontoDeleteRepository.save(p);
            } else if (pending instanceof PendingMemberInvite) {
                PendingMemberInvite p = (PendingMemberInvite) pending;
                p.markCompleted(PendingPaymentStatus.REJECTED);
                pendingMemberInviteRepository.save(p);
            }
        }

        return true;
    }

    private boolean handleMobileApproval(String signedMobileVerify, String requiredPrefix,
                                         AuthorisationStatus newStatus) {
        String mobileVerifyCode = mobileSecurityService.extractMobileVerifyCode(signedMobileVerify);

        if (!signedMobileVerify.startsWith(requiredPrefix)) {
            return false;
        }

        // Check pending payment
        Optional<PendingPayment> pendingPaymentOpt = pendingPaymentRepository.findByMobileVerifyCode(mobileVerifyCode);
        if (pendingPaymentOpt.isPresent()) {
            return handlePendingApproval(pendingPaymentOpt.get(), pendingPaymentOpt.get().getUser(),
                    signedMobileVerify, newStatus, "PAYMENT", mobileVerifyCode);
        }

        // Check pending payment update
        Optional<PendingPaymentUpdate> pendingUpdateOpt = pendingPaymentUpdateRepository.findByMobileVerifyCode(mobileVerifyCode);
        if (pendingUpdateOpt.isPresent()) {
            return handlePendingApproval(pendingUpdateOpt.get(), pendingUpdateOpt.get().getUser(),
                    signedMobileVerify, newStatus, "PAYMENT_UPDATE", mobileVerifyCode);
        }

        // Check pending payment delete
        Optional<PendingPaymentDelete> pendingDeleteOpt = pendingPaymentDeleteRepository.findByMobileVerifyCode(mobileVerifyCode);
        if (pendingDeleteOpt.isPresent()) {
            return handlePendingApproval(pendingDeleteOpt.get(), pendingDeleteOpt.get().getUser(),
                    signedMobileVerify, newStatus, "PAYMENT_DELETE", mobileVerifyCode);
        }

        // Check pending konto delete
        Optional<PendingKontoDelete> pendingKontoDeleteOpt = pendingKontoDeleteRepository.findByMobileVerifyCode(mobileVerifyCode);
        if (pendingKontoDeleteOpt.isPresent()) {
            return handlePendingApproval(pendingKontoDeleteOpt.get(), pendingKontoDeleteOpt.get().getUser(),
                    signedMobileVerify, newStatus, "KONTO_DELETE", mobileVerifyCode);
        }

        // Check pending member invite
        Optional<PendingMemberInvite> pendingInviteOpt = pendingMemberInviteRepository.findByMobileVerifyCode(mobileVerifyCode);
        if (pendingInviteOpt.isPresent()) {
            return handlePendingApproval(pendingInviteOpt.get(), pendingInviteOpt.get().getUser(),
                    signedMobileVerify, newStatus, "MEMBER_INVITE", mobileVerifyCode);
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