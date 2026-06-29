package com.sellerprofit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SellerProfitApplication {

    public static void main(String[] args) {
        SpringApplication.run(SellerProfitApplication.class, args);
    }
}
