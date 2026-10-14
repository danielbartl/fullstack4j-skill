package com.acme.shop.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.shop.TestcontainersConfiguration;
import com.acme.shop.WicketApplication;
import com.acme.shop.ordering.internal.Order;
import com.acme.shop.ordering.internal.OrderRepository;
import com.acme.shop.ordering.internal.Orders;
import java.util.List;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/// Renders the page in the whole application: proves markup, wicket:ids
/// and injection fit together. The rules themselves are proven in `OrdersTests`.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrdersPageTests {

    @Autowired ApplicationContext context;
    @Autowired Orders orders;
    @Autowired OrderRepository repository;

    WicketTester tester;

    @BeforeEach
    void startWicket() {
        repository.deleteAll();
        var application = new WicketApplication();
        application.getComponentInstantiationListeners()
            .add(new SpringComponentInjector(application, context));
        tester = new WicketTester(application);
    }

    @AfterEach
    void stopWicket() {
        tester.destroy();
    }

    @Test
    void listsOpenOrders() {
        orders.place("ACME", List.of(new Order.Line("Widget", 1)));

        tester.startPage(OrdersPage.class);

        tester.assertRenderedPage(OrdersPage.class);
        tester.assertLabel("list:orders:0:customer", "ACME");
    }

    @Test
    void shipsAnOrderFromTheList() {
        orders.place("ACME", List.of(new Order.Line("Widget", 1)));
        tester.startPage(OrdersPage.class);

        tester.clickLink("list:orders:0:ship");

        tester.assertComponentOnAjaxResponse("list");
        assertThat(orders.open()).isEmpty();
    }
}
