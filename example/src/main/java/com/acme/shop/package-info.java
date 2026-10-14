/// # Shop
/// > Take customers' orders, bill them and ship them.
///
/// ## Components
/// - `billing` listens to `ordering`'s `OrderShipped`; `ordering` knows no other slice.
/// - The root links to each slice's page and to nothing else in it.
///
/// ## Ubiquitous language
/// - Order — what a customer asked for, line by line. Owned by `ordering`.
/// - Invoice — what a shipped order is billed with. Owned by `billing`.
///
/// ## Stack
/// - fullstack4j · base package `com.acme.shop`
package com.acme.shop;
