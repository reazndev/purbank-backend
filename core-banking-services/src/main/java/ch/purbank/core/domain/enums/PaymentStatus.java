package ch.purbank.core.domain.enums;

public enum PaymentStatus {
    PENDING, // Waiting for execution
    EXECUTED, // Successfully executed
    FAILED, // Failed to execute
    CANCELLED // Cancelled by user
}