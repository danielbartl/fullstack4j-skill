package com.acme.shop.ordering;

import static dev.jbaby.wicket.testkit.locator.Locator.locate;
import static org.assertj.core.api.Assertions.assertThat;

import com.acme.shop.ordering.internal.Order;
import com.acme.shop.ordering.internal.OrderRepository;
import com.acme.shop.ordering.internal.Orders;
import dev.jbaby.fullstack4j.springboot.test.MongoDbReplicaSetConfiguration;
import dev.jbaby.wicket.oat.components.OatButton;
import dev.jbaby.wicket.springboot.test.WicketTest;
import dev.jbaby.wicket.testkit.WicketTestkit;
import java.util.List;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/// Renders the page in the whole application: proves markup, wicket:ids
/// and injection fit together. The rules themselves are proven in `OrdersTests`.
@WicketTest
@Import(MongoDbReplicaSetConfiguration.class)
class OrdersPageTests {

    @Autowired Orders orders;
    @Autowired OrderRepository repository;

    @BeforeEach
    void startEmpty() {
        repository.deleteAll();
    }

    @Test
    void listsOpenOrders(WicketTester tester, WicketTestkit kit) {
        orders.place("ACME", List.of(new Order.Line("Widget", 1)));

        tester.startPage(OrdersPage.class);

        tester.assertRenderedPage(OrdersPage.class);
        kit.assertRows(locate(ListView.class).withId("orders"), """
            | customer |
            | ACME     |
            """);
    }

    @Test
    void shipsAnOrderFromTheList(WicketTester tester, WicketTestkit kit) {
        var order = orders.place("ACME", List.of(new Order.Line("Widget", 1)));
        tester.startPage(OrdersPage.class);

        kit.click(locate(OatButton.class).withId("ship").inRowOf(order));

        kit.assertOnAjaxResponse(locate(WebMarkupContainer.class).withId("list"));
        assertThat(orders.open()).isEmpty();
    }
}
