package com.acme.shop;

import static dev.jbaby.wicket.testkit.locator.Locator.locate;

import com.acme.shop.billing.InvoicesPage;
import com.acme.shop.ordering.OrdersPage;
import dev.jbaby.fullstack4j.springboot.test.MongoDbReplicaSetConfiguration;
import dev.jbaby.wicket.springboot.test.WicketTest;
import dev.jbaby.wicket.testkit.WicketTestkit;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

@WicketTest
@Import(MongoDbReplicaSetConfiguration.class)
class StartPageTests {

    @Test
    void linksToEverySlice(WicketTester tester, WicketTestkit kit) {
        tester.startPage(StartPage.class);

        tester.assertRenderedPage(StartPage.class);
        tester.assertBookmarkablePageLink(kit.pathOf(locate(BookmarkablePageLink.class).withId("orders")),
            OrdersPage.class, new PageParameters());
        tester.assertBookmarkablePageLink(kit.pathOf(locate(BookmarkablePageLink.class).withId("invoices")),
            InvoicesPage.class, new PageParameters());
    }

    @Test
    void opensTheInvoices(WicketTester tester, WicketTestkit kit) {
        tester.startPage(StartPage.class);

        kit.click(locate(BookmarkablePageLink.class).withId("invoices"));

        tester.assertRenderedPage(InvoicesPage.class);
    }
}
