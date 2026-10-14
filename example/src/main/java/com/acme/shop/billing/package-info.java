/// # Billing
/// > Bill every order that ships.
///
/// ## Operations
/// - `bill-order`: raise an invoice for a shipped order (`Invoices.on(OrderShipped)`)
/// - `list-invoices`: list every invoice raised so far (`Invoices.all`)
///
/// ## Requirements
/// ### R1: Bill a shipped order
/// - R1.1 — When an order is shipped, the slice shall raise an invoice
///   for it.
/// - R1.2 — If the shipped order has an invoice already, then the slice
///   shall not raise a second one.
///   _(why: events are delivered at least once)_
///
/// ## Events
/// - publishes nothing
/// - listens to `OrderShipped` from `ordering` (R1)
///
/// ## Entities
/// - `Invoice`: one document per shipped order
///
/// ## Out of scope
/// - prices and payments
/// - sending invoices to customers
@ApplicationModule(displayName = "Billing", allowedDependencies = "ordering")
package com.acme.shop.billing;

import org.springframework.modulith.ApplicationModule;
