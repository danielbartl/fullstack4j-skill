package com.acme.shop.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.shop.TestcontainersConfiguration;
import com.acme.shop.ordering.internal.Order;
import com.acme.shop.ordering.internal.OrderRepository;
import com.acme.shop.ordering.internal.Orders;
import dev.jbaby.wicket.springboot.test.WicketTest;
import java.util.List;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/// Renders the page in the whole application: proves markup, wicket:ids
/// and injection fit together. The rules themselves are proven in `OrdersTests`.
@WicketTest
@Import(TestcontainersConfiguration.class)
class OrdersPageTests {

    @Autowired Orders orders;
    @Autowired OrderRepository repository;

    @BeforeEach
    void startEmpty() {
        repository.deleteAll();
    }

    @Test
    void listsOpenOrders(WicketTester tester) {
        orders.place("ACME", List.of(new Order.Line("Widget", 1)));

        tester.startPage(OrdersPage.class);

        tester.assertRenderedPage(OrdersPage.class);
        tester.assertLabel("list:orders:0:customer", "ACME");
    }

    @Test
    void shipsAnOrderFromTheList(WicketTester tester) {
        orders.place("ACME", List.of(new Order.Line("Widget", 1)));
        tester.startPage(OrdersPage.class);

        tester.clickLink("list:orders:0:ship");

        tester.assertComponentOnAjaxResponse("list");
        assertThat(orders.open()).isEmpty();
    }
}
