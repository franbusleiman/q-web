package com.busleiman.qweb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.*;

@SpringBootApplication
public class QWebApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(QWebApplication.class, args);

    }
}
