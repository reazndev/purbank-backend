package ch.purbank.core.service;

import ch.purbank.core.domain.AuthorisationRequest;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.AuthorisationStatus;
import ch.purbank.core.repository.AuthorisationRequestRepository;
import ch.purbank.core.security.SecureTokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthorisationService {

    private static final int MOBILE_VERIFY_TOKEN_LENGTH = 64;

    private final AuthorisationRequestRepository authorisationRequestRepository;
    private final MobileSecurityService mobileSecurityService;
    private final ActionExecutionService actionExecutionService;

    public AuthorisationService(AuthorisationRequestRepository authorisationRequestRepository,
            MobileSecurityService mobileSecurityService,
            ActionExecutionService actionExecutionService) {
        this.authorisationRequestRepository = authorisationRequestRepository;
        this.mobileSecurityService = mobileSecurityService;
        this.actionExecutionService = actionExecutionService;
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