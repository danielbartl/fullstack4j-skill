package com.acme.shop;

import com.acme.shop.billing.InvoicesPage;
import com.acme.shop.ordering.OrdersPage;
import dev.jbaby.wicket.oat.WicketOats;
import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;
import org.springframework.stereotype.Component;

@Component
public class WicketApplication extends WebApplication {

    @Override
    public Class<? extends Page> getHomePage() {
        return StartPage.class;
    }

    @Override
    protected void init() {
        super.init();
        WicketOats.install(this);
        mountPage("/orders", OrdersPage.class);
        mountPage("/invoices", InvoicesPage.class);
    }
}
