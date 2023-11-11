package com.busleiman.qweb.service;

import com.busleiman.qweb.dto.WalletConfirmation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Connection;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.*;

import java.nio.charset.StandardCharsets;

@Service
public class MessageReceiver {


    @Autowired
    private Mono<Connection> connectionMono;
    private final Receiver receiver;
    private final Sender sender;

    String queueE = "queue-E";
    String queueA = "queue-A";
    String queueX = "queue-X";

    private ObjectMapper objectMapper = new ObjectMapper();

    public MessageReceiver(Sender sender, Receiver receiver) {
        this.receiver = receiver;
        this.sender = sender;
    }

    @PostConstruct
    private void init() throws InterruptedException {
        send();
        consume();
        consume2();
    }


    @PreDestroy
    public void close() throws Exception {
        connectionMono.block().close();
    }

    public Disposable send(){
        String topicExchange = "topic-exchange";
        String routingKey = "topic.key";

        String message = "{\n" +
                "  \"id\": 1,\n" +
                "  \"buyerDni\": \"42384769\",\n" +
                "  \"sellerDni\": \"46171291\",\n" +
                "  \"usdAmount\": 1,\n" +
                "  \"javaCoinPrice\": 500\n" +
                "}";


        /**
         *Se envía la orden a registrar mediante dos queues bindeadas a un topico,
         * una sera consumida por el banco, que validara y descontara el saldo del usuario,
         * y la otra por la wallet, que valida la existencia del usuario, y si no lo crea.
         */
      return  sender.send(Mono.just(new OutboundMessage(
                        topicExchange, routingKey, message.getBytes()
                )))
                .subscribe();
    }

    public Disposable consume() {

        return receiver.consumeAutoAck(queueE).flatMap(message -> {

            String json = new String(message.getBody(), StandardCharsets.UTF_8);

            System.out.println("ORDER MESSAGE RECEIVED: " + json);

            return Mono.just(json);
        }).subscribe();
    }

    /**
     * Se recibe el mensaje orden confirmada,
     * y se reenvía un mensaje con el id de la orden,
     * el estado aceptado, y el id del vendedor que toma la orden. La queue luego sera consumida
     * por la wallet.
     */
    public Disposable consume2() {

        return receiver.consumeAutoAck(queueX).flatMap(message -> {

            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            WalletConfirmation walletConfirmation;
            System.out.println(json);

            try {
                walletConfirmation = objectMapper.readValue(json, WalletConfirmation.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            //Un vendedor toma la orden
            walletConfirmation.setSellerDni("46171291");
            walletConfirmation.setOrderState("ACCEPTED");
            return Mono.just(walletConfirmation);
        }).map(walletConfirmation -> sender.sendWithPublishConfirms(outboundMessage(walletConfirmation, queueA))
                    .subscribe()
        ).subscribe();
    }


    private Flux<OutboundMessage> outboundMessage(Object message, String queue) {

        String json;
        try {
            json = objectMapper.writeValueAsString(message);

            return Flux.just(new OutboundMessage(
                    "",
                    queue,
                    json.getBytes()));

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
