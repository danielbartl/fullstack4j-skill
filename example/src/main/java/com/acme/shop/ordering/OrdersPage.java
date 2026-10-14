package com.acme.shop.ordering;

import com.acme.shop.ordering.internal.Order;
import com.acme.shop.ordering.internal.Orders;
import dev.jbaby.wicket.oat.Oat;
import dev.jbaby.wicket.oat.OatVariant;
import dev.jbaby.wicket.oat.components.OatButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

public class OrdersPage extends WebPage {

    @SpringBean
    private Orders orders;

    public OrdersPage() {
        var list = new WebMarkupContainer("list");
        add(list.setOutputMarkupId(true));

        var open = LoadableDetachableModel.of(orders::open);
        list.add(new ListView<>("orders", open) {
            @Override
            protected void populateItem(ListItem<Order> item) {
                Order order = item.getModelObject();
                String id = order.id();
                item.add(new Label("customer", order.customer()));
                item.add(OatButton.onClick("ship", "Ship", ajax -> {
                    orders.ship(id);
                    Oat.toast(ajax, "Shipped", OatVariant.SUCCESS);
                    ajax.add(list);
                }));
            }
        });
    }
}
