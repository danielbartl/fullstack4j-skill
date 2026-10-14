package com.acme.shop.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.shop.TestcontainersConfiguration;
import com.acme.shop.billing.internal.Invoice;
import com.acme.shop.billing.internal.InvoiceRepository;
import com.acme.shop.billing.internal.Invoices;
import com.acme.shop.ordering.OrderShipped;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.util.AopTestUtils;

/// Each test starts with the id of the requirement it proves.
/// The requirements live in this slice's `package-info.java`.
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class InvoicesTests {

    @Autowired Invoices invoices;
    @Autowired InvoiceRepository repository;

    @BeforeEach
    void startEmpty() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("R1.1 a shipped order gets an invoice")
    void shippedOrderIsBilled(Scenario scenario) {
        scenario.publish(new OrderShipped("order-1"))
            .andWaitForStateChange(invoices::all, all -> !all.isEmpty())
            .andVerify(all -> assertThat(all).extracting(Invoice::orderId).containsExactly("order-1"));
    }

    @Test
    @DisplayName("R1.2 an order shipped twice is billed once")
    void orderIsBilledOnce() {
        // through the Spring proxy the listener runs asynchronously, so
        // deliver the event twice to the bean itself, one after the other
        Invoices listener = AopTestUtils.getTargetObject(invoices);
        listener.on(new OrderShipped("order-1"));
        listener.on(new OrderShipped("order-1"));

        assertThat(invoices.all()).hasSize(1);
    }
}
