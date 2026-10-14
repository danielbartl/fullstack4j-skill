package com.acme.shop;

import com.acme.shop.billing.InvoicesPage;
import com.acme.shop.ordering.OrdersPage;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

public class StartPage extends WebPage {

    public StartPage() {
        add(new BookmarkablePageLink<>("orders", OrdersPage.class));
        add(new BookmarkablePageLink<>("invoices", InvoicesPage.class));
    }
}
