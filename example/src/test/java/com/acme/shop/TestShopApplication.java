package com.acme.shop;

import org.springframework.boot.SpringApplication;

public class TestShopApplication {

    public static void main(String[] args) {
        SpringApplication.from(ShopApplication::main)
            .with(TestcontainersConfiguration.class, DemoData.class)
            .run(args);
    }

}
