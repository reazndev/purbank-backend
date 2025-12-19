package ch.purbank.core.service;

import ch.purbank.core.domain.AuthorisationRequest;
import ch.purbank.core.domain.PendingPayment;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import ch.purbank.core.repository.PendingPaymentRepository;
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
    private final PaymentService paymentService;

    @Transactional
    public void executeAction(AuthorisationRequest request) {
        String actionType = request.getActionType();

        if ("PAYMENT".equals(actionType)) {
            executePaymentAction(request.getMobileVerifyCode());
        } else {
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
}