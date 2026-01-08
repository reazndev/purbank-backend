package ch.purbank.core.domain.enums;

public enum MemberRole {
    OWNER, // Can do everything including closing konto and inviting users
    MANAGER, // Can manage transactions, create, delete, update them
    VIEWER // Read-only access to transactions
}