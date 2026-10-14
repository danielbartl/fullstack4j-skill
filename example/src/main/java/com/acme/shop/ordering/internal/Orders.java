package com.acme.shop.ordering.internal;

import com.acme.shop.ordering.OrderShipped;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class Orders {

    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    Orders(OrderRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public Order place(String customer, List<Order.Line> lines) {
        return repository.save(Order.open(customer, lines));
    }

    public List<Order> open() {
        return repository.findByShippedAtIsNull();
    }

    public void ship(String id) {
        var order = repository.findById(id).orElseThrow().ship();
        repository.save(order);
        // billing and shipping react to this; ordering never calls them
        events.publishEvent(new OrderShipped(order.id()));
    }
}
