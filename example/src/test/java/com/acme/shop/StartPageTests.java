package com.acme.shop;

import com.acme.shop.billing.InvoicesPage;
import com.acme.shop.ordering.OrdersPage;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StartPageTests {

    @Autowired ApplicationContext context;

    WicketTester tester;

    @BeforeEach
    void startWicket() {
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
    void linksToEverySlice() {
        tester.startPage(StartPage.class);

        tester.assertRenderedPage(StartPage.class);
        tester.assertBookmarkablePageLink("orders", OrdersPage.class, new org.apache.wicket.request.mapper.parameter.PageParameters());
        tester.assertBookmarkablePageLink("invoices", InvoicesPage.class, new org.apache.wicket.request.mapper.parameter.PageParameters());
    }

    @Test
    void opensTheInvoices() {
        tester.startPage(StartPage.class);

        tester.clickLink("invoices");

        tester.assertRenderedPage(InvoicesPage.class);
    }
}
