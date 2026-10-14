package com.acme.shop;

import com.acme.shop.billing.InvoicesPage;
import com.acme.shop.ordering.OrdersPage;
import dev.jbaby.wicket.springboot.test.WicketTest;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

@WicketTest
@Import(TestcontainersConfiguration.class)
class StartPageTests {

    @Test
    void linksToEverySlice(WicketTester tester) {
        tester.startPage(StartPage.class);

        tester.assertRenderedPage(StartPage.class);
        tester.assertBookmarkablePageLink("orders", OrdersPage.class, new PageParameters());
        tester.assertBookmarkablePageLink("invoices", InvoicesPage.class, new PageParameters());
    }

    @Test
    void opensTheInvoices(WicketTester tester) {
        tester.startPage(StartPage.class);

        tester.clickLink("invoices");

        tester.assertRenderedPage(InvoicesPage.class);
    }
}
