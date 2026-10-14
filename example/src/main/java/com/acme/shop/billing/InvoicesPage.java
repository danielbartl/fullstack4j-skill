package com.acme.shop.billing;

import com.acme.shop.billing.internal.Invoice;
import com.acme.shop.billing.internal.Invoices;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

public class InvoicesPage extends WebPage {

    @SpringBean
    private Invoices invoices;

    public InvoicesPage() {
        add(new ListView<>("invoices", LoadableDetachableModel.of(invoices::all)) {
            @Override
            protected void populateItem(ListItem<Invoice> item) {
                Invoice invoice = item.getModelObject();
                item.add(new Label("order", invoice.orderId()));
                item.add(new Label("raised", invoice.raisedAt()));
            }
        });
    }
}
