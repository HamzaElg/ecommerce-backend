// File: src/main/java/com/ecommerce/EcommerceApplication.java
package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching      // Enables Redis caching via @Cacheable annotations
@EnableScheduling   // Enables @Scheduled tasks (e.g. reservation timeout job)
public class EcommerceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
    }
}
