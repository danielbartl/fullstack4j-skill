package com.acme.shop.ordering.internal;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/// Stored as one document, lines included: no join table, no mapping layer,
/// no migration script for a new field.
@Document("orders")
public record Order(@Id String id, String customer, List<Line> lines, Instant shippedAt) {

    public record Line(String product, int quantity) {}

    public static Order open(String customer, List<Line> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs lines");
        }
        return new Order(null, customer, List.copyOf(lines), null);
    }

    public Order ship() {
        if (shippedAt != null) {
            throw new IllegalStateException("already shipped");
        }
        return new Order(id, customer, lines, Instant.now());
    }
}
