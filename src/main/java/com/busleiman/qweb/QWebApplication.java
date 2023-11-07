package com.busleiman.qweb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.*;

@SpringBootApplication
public class QWebApplication {

    private static Sender sender;

    @Autowired
    public QWebApplication(Sender sender) {
        QWebApplication.sender = sender;
    }

    public static void main(String[] args) {
        SpringApplication.run(QWebApplication.class, args);


        String topicExchange = "topic-exchange";
        String routingKey = "topic.key";

        String queueA = "queue-A";
        String queueB = "queue-B";
        String queueC = "queue-C";


        String message = "{\n" +
                "  \"id\": 1,\n" +
                "  \"buyerDni\": \"42384769\",\n" +
                "  \"sellerDni\": \"46171291\",\n" +
                "  \"usdAmount\": 1,\n" +
                "  \"javaCoinPrice\": 500\n" +
                "}";

        String messageConfirmation = "{\n" +
                "  \"id\": 1,\n" +
                "  \"sellerDni\": \"46171291\",\n" +
                "  \"orderState\": \"ACCEPTED\"\n" +
                "}";

        /**
         *Se envía la orden a registrar mediante dos queues bindeadas a un topico,
         * una sera consumida por el banco, que validara y descontara el saldo del usuario,
         * y la otra por la wallet, que valida la existencia del usuario, y si no lo crea.
         */
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
                        topicExchange, routingKey, message.getBytes()
                ))))
                .doOnTerminate(() -> sender.close())
                .subscribe();

        /**
         * Se confirma la orden, mandando un mensaje con el id de la orden,
         * el estado aceptado, y el id del vendedor. La queue luego sera consumida
         * por la wallet.
         */
        sender.declareQueue(QueueSpecification.queue(queueA))
                .thenMany(sender.sendWithPublishConfirms(Flux.just(new OutboundMessage(
                        queueA,
                        queueA,
                        messageConfirmation.getBytes()))))
                .subscribe();
    }
}
