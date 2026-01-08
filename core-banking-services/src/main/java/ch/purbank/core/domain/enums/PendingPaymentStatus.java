package ch.purbank.core.domain.enums;

public enum PendingPaymentStatus {
    PENDING,    // Waiting for mobile approval
    APPROVED,   // Approved via mobile
    REJECTED,   // Rejected via mobile
    EXPIRED     // Timed out without approval
}