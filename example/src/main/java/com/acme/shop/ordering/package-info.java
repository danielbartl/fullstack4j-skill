/// # Ordering
/// > Take a customer's order and see it through until it ships.
///
/// ## Operations
/// - `place-order`: take an order for a customer (`Orders.place`)
/// - `open-orders`: list what still has to ship (`Orders.open`)
/// - `ship-order`: hand an open order over to shipping (`Orders.ship`)
///
/// ## Requirements
/// ### R1: Place an order
/// - R1.1 — When a customer places an order with at least one line,
///   the slice shall list it as open.
/// - R1.2 — If an order has no lines, then the slice shall reject it.
///
/// ### R2: Ship an order
/// - R2.1 — When an open order is shipped, the slice shall no longer
///   list it as open.
/// - R2.2 — When an open order is shipped, the slice shall publish
///   `OrderShipped`.
/// - R2.3 — If the order has already shipped, then the slice shall
///   reject the request.
///
/// ## Events
/// - publishes `OrderShipped` (R2.2)
/// - listens to nothing
///
/// ## Entities
/// - `Order`: one document with its customer, lines and status
///
/// ## Out of scope
/// - prices and invoices (`billing`)
/// - parcels and carriers (`shipping`)
@ApplicationModule(displayName = "Ordering", allowedDependencies = {})
package com.acme.shop.ordering;

import org.springframework.modulith.ApplicationModule;
