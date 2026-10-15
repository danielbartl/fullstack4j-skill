package com.acme.shop;

import dev.jbaby.fullstack4j.springboot.test.MongoDbReplicaSetConfiguration;
import org.springframework.boot.SpringApplication;

public class TestShopApplication {

    public static void main(String[] args) {
        SpringApplication.from(ShopApplication::main)
            .with(MongoDbReplicaSetConfiguration.class, DemoData.class)
            .run(args);
    }

}
