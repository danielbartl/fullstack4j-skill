package com.acme.shop.ordering;

/// Lives in the base package, so other slices may use it; everything in `internal` stays hidden.
public record OrderShipped(String orderId) {}
