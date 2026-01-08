package ch.purbank.core.service;

import ch.purbank.core.domain.*;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import ch.purbank.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionExecutionService {

    private final PendingPaymentRepository pendingPaymentRepository;
    private final PendingPaymentUpdateRepository pendingPaymentUpdateRepository;
    private final PendingPaymentDeleteRepository pendingPaymentDeleteRepository;
    private final PendingKontoDeleteRepository pendingKontoDeleteRepository;
    private final PendingMemberInviteRepository pendingMemberInviteRepository;
    private final PaymentService paymentService;
    private final KontoService kontoService;

    @Transactional
    public void executeAction(AuthorisationRequest request) {
        String actionType = request.getActionType();

        switch (actionType) {
            case "PAYMENT":
                executePaymentAction(request.getMobileVerifyCode());
                break;
            case "PAYMENT_UPDATE":
                executePaymentUpdateAction(request.getMobileVerifyCode());
                break;
            case "PAYMENT_DELETE":
                executePaymentDeleteAction(request.getMobileVerifyCode());
                break;
            case "KONTO_DELETE":
                executeKontoDeleteAction(request.getMobileVerifyCode());
                break;
            case "MEMBER_INVITE":
                executeMemberInviteAction(request.getMobileVerifyCode());
                break;
            default:
                log.warn("Unknown action type: {}", actionType);
        }
    }

    private void executePaymentAction(String mobileVerifyCode) {
        Optional<PendingPayment> pendingPaymentOpt = pendingPaymentRepository
                .findByMobileVerifyCodeAndStatus(mobileVerifyCode, PendingPaymentStatus.PENDING);

        if (pendingPaymentOpt.isEmpty()) {
            log.error("No pending payment found for mobile-verify code");
            throw new IllegalArgumentException("Pending payment not found or already processed");
        }

        PendingPayment pendingPayment = pendingPaymentOpt.get();

        if (pendingPayment.isExpired()) {
            log.warn("Pending payment {} has expired", pendingPayment.getId());
            pendingPayment.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingPaymentRepository.save(pendingPayment);
            throw new IllegalArgumentException("Pending payment has expired");
        }

        // Execute the approved payment
        paymentService.executeApprovedPayment(pendingPayment);

        log.info("Payment action executed successfully for pending payment {}", pendingPayment.getId());
    }

    private void executePaymentUpdateAction(String mobileVerifyCode) {
        Optional<PendingPaymentUpdate> pendingUpdateOpt = pendingPaymentUpdateRepository
                .findByMobileVerifyCodeAndStatus(mobileVerifyCode, PendingPaymentStatus.PENDING);

        if (pendingUpdateOpt.isEmpty()) {
            log.error("No pending payment update found for mobile-verify code");
            throw new IllegalArgumentException("Pending payment update not found or already processed");
        }

        PendingPaymentUpdate pendingUpdate = pendingUpdateOpt.get();

        if (pendingUpdate.isExpired()) {
            log.warn("Pending payment update {} has expired", pendingUpdate.getId());
            pendingUpdate.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingPaymentUpdateRepository.save(pendingUpdate);
            throw new IllegalArgumentException("Pending payment update has expired");
        }

        // Execute the approved payment update
        paymentService.executeApprovedPaymentUpdate(pendingUpdate);
        pendingUpdate.markCompleted(PendingPaymentStatus.APPROVED);
        pendingPaymentUpdateRepository.save(pendingUpdate);

        log.info("Payment update action executed successfully for pending update {}", pendingUpdate.getId());
    }

    private void executePaymentDeleteAction(String mobileVerifyCode) {
        Optional<PendingPaymentDelete> pendingDeleteOpt = pendingPaymentDeleteRepository
                .findByMobileVerifyCodeAndStatus(mobileVerifyCode, PendingPaymentStatus.PENDING);

        if (pendingDeleteOpt.isEmpty()) {
            log.error("No pending payment delete found for mobile-verify code");
            throw new IllegalArgumentException("Pending payment delete not found or already processed");
        }

        PendingPaymentDelete pendingDelete = pendingDeleteOpt.get();

        if (pendingDelete.isExpired()) {
            log.warn("Pending payment delete {} has expired", pendingDelete.getId());
            pendingDelete.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingPaymentDeleteRepository.save(pendingDelete);
            throw new IllegalArgumentException("Pending payment delete has expired");
        }

        // Execute the approved payment delete
        paymentService.executeApprovedPaymentDelete(pendingDelete);
        pendingDelete.markCompleted(PendingPaymentStatus.APPROVED);
        pendingPaymentDeleteRepository.save(pendingDelete);

        log.info("Payment delete action executed successfully for pending delete {}", pendingDelete.getId());
    }

    private void executeKontoDeleteAction(String mobileVerifyCode) {
        Optional<PendingKontoDelete> pendingDeleteOpt = pendingKontoDeleteRepository
                .findByMobileVerifyCodeAndStatus(mobileVerifyCode, PendingPaymentStatus.PENDING);

        if (pendingDeleteOpt.isEmpty()) {
            log.error("No pending konto delete found for mobile-verify code");
            throw new IllegalArgumentException("Pending konto delete not found or already processed");
        }

        PendingKontoDelete pendingDelete = pendingDeleteOpt.get();

        if (pendingDelete.isExpired()) {
            log.warn("Pending konto delete {} has expired", pendingDelete.getId());
            pendingDelete.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingKontoDeleteRepository.save(pendingDelete);
            throw new IllegalArgumentException("Pending konto delete has expired");
        }

        // Execute the approved konto delete
        kontoService.executeApprovedKontoDelete(pendingDelete);
        pendingDelete.markCompleted(PendingPaymentStatus.APPROVED);
        pendingKontoDeleteRepository.save(pendingDelete);

        log.info("Konto delete action executed successfully for pending delete {}", pendingDelete.getId());
    }

    private void executeMemberInviteAction(String mobileVerifyCode) {
        Optional<PendingMemberInvite> pendingInviteOpt = pendingMemberInviteRepository
                .findByMobileVerifyCodeAndStatus(mobileVerifyCode, PendingPaymentStatus.PENDING);

        if (pendingInviteOpt.isEmpty()) {
            log.error("No pending member invite found for mobile-verify code");
            throw new IllegalArgumentException("Pending member invite not found or already processed");
        }

        PendingMemberInvite pendingInvite = pendingInviteOpt.get();

        if (pendingInvite.isExpired()) {
            log.warn("Pending member invite {} has expired", pendingInvite.getId());
            pendingInvite.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingMemberInviteRepository.save(pendingInvite);
            throw new IllegalArgumentException("Pending member invite has expired");
        }

        // Execute the approved member invite
        kontoService.executeApprovedMemberInvite(pendingInvite);
        pendingInvite.markCompleted(PendingPaymentStatus.APPROVED);
        pendingMemberInviteRepository.save(pendingInvite);

        log.info("Member invite action executed successfully for pending invite {}", pendingInvite.getId());
    }
}