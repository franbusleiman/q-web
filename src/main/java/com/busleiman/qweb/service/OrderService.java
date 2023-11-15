package com.busleiman.qweb.service;

import com.busleiman.qweb.dto.OrderRequest;
import com.busleiman.qweb.dto.WalletConfirmation;
import com.busleiman.qweb.model.Order;
import com.busleiman.qweb.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Connection;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.*;

import java.nio.charset.StandardCharsets;

import static com.busleiman.qweb.utils.Constants.*;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private Mono<Connection> connectionMono;
    private final Receiver receiver;
    private final Sender sender;
    private final ModelMapper modelMapper;

    private ObjectMapper objectMapper = new ObjectMapper();

    public OrderService(OrderRepository orderRepository,
                        ModelMapper modelMapper, Receiver receiver, Sender sender) {
        this.orderRepository = orderRepository;
        this.modelMapper = modelMapper;
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


        Order order = Order.builder()
                .id(1L)
                .buyerDni("42384769")
                .usdAmount(10L)
                .javaCoinPrice(500L)
                .build();

        OrderRequest orderRequest = modelMapper.map(orderRepository.save(order), OrderRequest.class);


        /**
         *Se envía la orden a registrar mediante una exchange fanout,
         * sera consumida por el banco, que validara y descontara el saldo del usuario,
         * y por la wallet, que valida la existencia del usuario, y si no lo crea.
         */
      return  sender.send(outboundMessage(orderRequest, "", FANOUT_EXCHANGE))
                .subscribe();
    }

    public Disposable consume() {

        return receiver.consumeAutoAck(QUEUE_E).flatMap(message -> {

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

        return receiver.consumeAutoAck(QUEUE_G).flatMap(message -> {

            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            WalletConfirmation walletConfirmation;

            try {
                walletConfirmation = objectMapper.readValue(json, WalletConfirmation.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            return orderRepository.findById(walletConfirmation.getId())
                    .flatMap(order -> {

                        if(walletConfirmation.getBankAccepted()!=null){
                            order.setBankAccepted(walletConfirmation.getBankAccepted());
                        }
                        else if(walletConfirmation.getWalletAccepted()!=null){
                            order.setBankAccepted(walletConfirmation.getWalletAccepted());
                        }

                        return orderRepository.save(order)
                                .flatMap(orderSaved ->{

                                    if(order.getWalletAccepted()== null || order.getBankAccepted()==null){
                                        return Mono.empty();
                                    }
                                    else if(!order.getWalletAccepted() && !order.getBankAccepted()) {
                                        return Mono.empty();
                                    }
                                    else if (!order.getWalletAccepted() || !order.getBankAccepted()){

                                        walletConfirmation.setOrderState("NOT_ACCEPTED");
                                        return Mono.just(walletConfirmation);
                                    }

                                    else {
                                        //Un vendedor toma la orden
                                        walletConfirmation.setSellerDni("46171291");
                                        walletConfirmation.setOrderState("ACCEPTED");
                                        return Mono.just(walletConfirmation);
                                    }
                                });
                    });


        }).map(walletConfirmation -> sender.send(outboundMessage(walletConfirmation, QUEUE_A, QUEUES_EXCHANGE))
                    .subscribe()
        ).subscribe();
    }


    private Flux<OutboundMessage> outboundMessage(Object message, String routingKey, String exchange) {

        String json;
        try {
            json = objectMapper.writeValueAsString(message);

            return Flux.just(new OutboundMessage(
                    exchange,
                    routingKey,
                    json.getBytes()));

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
