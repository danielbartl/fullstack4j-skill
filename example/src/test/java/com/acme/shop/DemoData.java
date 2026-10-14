package com.acme.shop;

import com.acme.shop.ordering.internal.Order;
import com.acme.shop.ordering.internal.Orders;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/// A few orders for `TestShopApplication`, so the pages have something to show.
/// One of them ships, so `billing` raises an invoice for it. Test code only.
@TestConfiguration(proxyBeanMethods = false)
class DemoData {

    @Bean
    ApplicationRunner placeDemoOrders(Orders orders) {
        return args -> {
            orders.place("ACME", List.of(new Order.Line("Widget", 3)));
            orders.place("Globex", List.of(new Order.Line("Gadget", 1), new Order.Line("Gizmo", 2)));
            var shipped = orders.place("Initech", List.of(new Order.Line("Stapler", 1)));
            orders.ship(shipped.id());
        };
    }
}
