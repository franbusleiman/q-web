package com.busleiman.qweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@EnableR2dbcRepositories
@SpringBootApplication
public class QWebApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(QWebApplication.class, args);

    }
}
