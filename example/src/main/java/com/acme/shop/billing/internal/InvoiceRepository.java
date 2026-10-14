package com.acme.shop.billing.internal;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvoiceRepository extends MongoRepository<Invoice, String> {

    boolean existsByOrderId(String orderId);
}
