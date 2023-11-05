package com.busleiman.qweb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.*;

@SpringBootApplication
public class QWebApplication {

    private static Sender sender;

    @Autowired
    public QWebApplication(Sender sender) {
        this.sender = sender;
    }

    public static void main(String[] args) {
        SpringApplication.run(QWebApplication.class, args);


        String topicExchange = "topic-exchange";
        String routingKey = "topic.key";

        String queueB = "queue-B";
        String queueC = "queue-C";

        sender.declareExchange(ExchangeSpecification.exchange(topicExchange).type("topic"))
                .then(sender.declareQueue(QueueSpecification.queue(queueB))
                        .then(sender.bind(new BindingSpecification()
                                .exchange(topicExchange)
                                .queue(queueB)
                                .routingKey(routingKey))
                        ))
                .then(
                        sender.declareQueue(QueueSpecification.queue(queueC))
                                .then(sender.bind(new BindingSpecification()
                                        .exchange(topicExchange)
                                        .queue(queueC)
                                        .routingKey(routingKey))
                                ))
                .then(sender.send(Mono.just(new OutboundMessage(
                        topicExchange, routingKey, "Mensaje de ejemplo".getBytes()
                ))))
                .doOnTerminate(() -> sender.close())
                .subscribe();


    }

}
