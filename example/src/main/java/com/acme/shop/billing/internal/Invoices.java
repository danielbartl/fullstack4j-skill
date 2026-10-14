package com.acme.shop.billing.internal;

import com.acme.shop.ordering.OrderShipped;
import java.util.List;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class Invoices {

    private final InvoiceRepository repository;

    Invoices(InvoiceRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(OrderShipped event) {
        if (!repository.existsByOrderId(event.orderId())) {
            repository.save(Invoice.raise(event.orderId()));
        }
    }

    public List<Invoice> all() {
        return repository.findAll();
    }
}
