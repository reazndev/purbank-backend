package ch.purbank.core.domain;

import ch.purbank.core.domain.enums.PendingPaymentStatus;

public interface PendingRequest {
    String getDeviceId();
    PendingPaymentStatus getStatus();
    boolean isExpired();
    void markCompleted(PendingPaymentStatus newStatus);
}
