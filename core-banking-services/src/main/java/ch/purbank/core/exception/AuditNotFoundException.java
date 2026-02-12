package ch.purbank.core.exception;

import java.util.UUID;

public class AuditNotFoundException extends RuntimeException {
    public AuditNotFoundException(UUID id) {
        super("Audit not found with id: " + id);
    }

    public AuditNotFoundException(String message) {
        super(message);
    }
}