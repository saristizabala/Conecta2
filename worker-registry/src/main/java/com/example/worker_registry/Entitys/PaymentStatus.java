package com.example.worker_registry.Entitys;

public enum PaymentStatus {
    NOT_REQUIRED,
    PENDING,
    REQUIRES_ACTION,
    SUCCEEDED,
    FAILED;

    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED;
    }

    public static PaymentStatus fromGatewayStatus(String status) {
        if (status == null || status.isBlank()) {
            return PENDING;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case "SUCCEEDED" -> SUCCEEDED;
            case "FAILED", "CANCELED", "CANCELLED" -> FAILED;
            case "REQUIRES_ACTION" -> REQUIRES_ACTION;
            default -> PENDING;
        };
    }
}
