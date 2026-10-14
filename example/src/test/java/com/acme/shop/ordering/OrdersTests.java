package com.acme.shop.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.shop.TestcontainersConfiguration;
import com.acme.shop.ordering.internal.Order;
import com.acme.shop.ordering.internal.OrderRepository;
import com.acme.shop.ordering.internal.Orders;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

/// Each test starts with the id of the requirement it proves.
/// The requirements live in this slice's `package-info.java`.
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class OrdersTests {

    static final List<Order.Line> ONE_WIDGET = List.of(new Order.Line("Widget", 1));

    @Autowired Orders orders;
    @Autowired OrderRepository repository;

    @BeforeEach
    void startEmpty() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("R1.1 a placed order is listed as open")
    void placedOrderIsOpen() {
        var order = orders.place("ACME", ONE_WIDGET);

        assertThat(orders.open()).extracting(Order::id).containsExactly(order.id());
    }

    @Test
    @DisplayName("R1.2 an order without lines is rejected")
    void orderWithoutLinesIsRejected() {
        assertThatThrownBy(() -> orders.place("ACME", List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("R2.1 a shipped order is no longer open")
    void shippedOrderIsNotOpen() {
        var order = orders.place("ACME", ONE_WIDGET);

        orders.ship(order.id());

        assertThat(orders.open()).isEmpty();
    }

    @Test
    @DisplayName("R2.2 shipping an order publishes OrderShipped")
    void shippingPublishesOrderShipped(Scenario scenario) {
        var order = orders.place("ACME", ONE_WIDGET);

        scenario.stimulate(() -> orders.ship(order.id()))
            .andWaitForEventOfType(OrderShipped.class)
            .toArriveAndVerify(event -> assertThat(event.orderId()).isEqualTo(order.id()));
    }

    @Test
    @DisplayName("R2.3 an order cannot ship twice")
    void orderCannotShipTwice() {
        var order = orders.place("ACME", ONE_WIDGET);
        orders.ship(order.id());

        assertThatThrownBy(() -> orders.ship(order.id()))
            .isInstanceOf(IllegalStateException.class);
    }
}
