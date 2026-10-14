package com.acme.shop.billing.internal;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/// Keeps the shipped order's id, not a copy of the order: billing only knows
/// what `OrderShipped` tells it.
@Document("invoices")
public record Invoice(@Id String id, String orderId, Instant raisedAt) {

    public static Invoice raise(String orderId) {
        return new Invoice(null, orderId, Instant.now());
    }
}
