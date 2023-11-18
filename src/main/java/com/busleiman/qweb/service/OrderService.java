package com.busleiman.qweb.service;

import com.busleiman.qweb.dto.OrderConfirmation;
import com.busleiman.qweb.dto.OrderRequest;
import com.busleiman.qweb.dto.SourceType;
import com.busleiman.qweb.model.Order;
import com.busleiman.qweb.model.OrderState;
import com.busleiman.qweb.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Connection;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

import java.nio.charset.StandardCharsets;

import static com.busleiman.qweb.utils.Constants.*;

@Service
@Slf4j
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private Mono<Connection> connectionMono;
    private final Receiver receiver;
    private final Sender sender;
    private final ModelMapper modelMapper;

    ReactiveTransactionManager reactiveTransactionManager;

    private ObjectMapper objectMapper = new ObjectMapper();

    public OrderService(OrderRepository orderRepository,
                        ModelMapper modelMapper, Receiver receiver, Sender sender, ReactiveTransactionManager reactiveTransactionManager) {
        this.orderRepository = orderRepository;
        this.modelMapper = modelMapper;
        this.receiver = receiver;
        this.sender = sender;
        this.reactiveTransactionManager = reactiveTransactionManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        send().subscribe();
        consume().subscribe();
        consume2().subscribe();
    }


    @PreDestroy
    public void close() throws Exception {
        connectionMono.block().close();
    }

    /**
     * Se envía la orden a registrar mediante una exchange fanout,
     * sera consumida por el banco, que validara y descontara el saldo del usuario,
     * y por la wallet, que valida la existencia del usuario, y si no lo crea.
     */
    public Mono<Void> send() {

        Order order = Order.builder()
                .id(1L)
                .buyerDni("42384769")
                .usdAmount(100.0)
                .bankAccepted(OrderState.IN_PROGRESS)
                .walletAccepted(OrderState.IN_PROGRESS)
                .orderState(OrderState.IN_PROGRESS)
                .javaCoinPrice(500.0)
                .build();

        order.calculateJavaCoinsAmount();

        return orderRepository.save(order)
                .map(order1 -> modelMapper.map(order1, OrderRequest.class))
                .flatMap(orderRequest -> sender.send(outboundMessage(orderRequest, "", FANOUT_EXCHANGE)));
    }


    /**
     * Se recibe el mensaje order confirmation por parte del servicio Wallet,
     * se registra si la misma aceptó o no la operación.
     * <p>
     * En el caso de no haberse aceptado la orden, se envía un mensaje de orden no aceptada,
     * para mantener consistentes los otros microservicios,
     * en el caso de aceptarse la orden, pero que el banco no la haya acepado aún, se guarda
     * y no se envía mensaje, esperando confirmación del mismo,
     * en el caso de que ambos microservicios acepten la orden se envía un mensaje con los datos de un
     * vendedor que toma la orden.
     */
    @Transactional
    public Flux<Void> consume() {
        return receiver.consumeAutoAck(QUEUE_G).flatMap(message -> {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            OrderConfirmation orderConfirmation;

            try {
                orderConfirmation = objectMapper.readValue(json, OrderConfirmation.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            return orderRepository.findById(orderConfirmation.getId())
                    .flatMap(order -> {
                        OrderState orderState = orderConfirmation.getOrderState();

                        // Chequea si la confirmación proviene del banco o de la billetera
                        if (orderConfirmation.getSourceType() == SourceType.BANK) {
                            order.setBankAccepted(orderState);
                        } else if (orderConfirmation.getSourceType() == SourceType.WALLET) {
                            order.setWalletAccepted(orderState);
                        }

                        return orderRepository.save(order)
                                .flatMap(orderSaved -> {
                                    if (orderState.equals(OrderState.NOT_ACCEPTED) && !orderSaved.getOrderState().equals(OrderState.NOT_ACCEPTED)) {
                                        // Se setea en "no aceptado" el estado de la orden y se envía el error.
                                        orderSaved.setOrderState(OrderState.NOT_ACCEPTED);
                                        orderConfirmation.setSellerDni("");
                                        return orderRepository.save(orderSaved).thenReturn(orderConfirmation);
                                    } else if (orderSaved.getWalletAccepted() == OrderState.IN_PROGRESS || orderSaved.getBankAccepted() == OrderState.IN_PROGRESS) {
                                        // Se espera a la confirmación de la otra parte
                                        return Mono.empty();
                                    } else {
                                        // Un vendedor toma la orden y se envía la confirmación.
                                        orderConfirmation.setSellerDni("46171291");
                                        orderConfirmation.setOrderState(OrderState.ACCEPTED);
                                        return Mono.just(orderConfirmation);
                                    }
                                });
                    });
        }).flatMap(confirmation -> sender.send(outboundMessage(confirmation, QUEUE_A, QUEUES_EXCHANGE)));
    }

    /**
     * Se recibe el mensaje order confirmation final luego de que la transacción se haya llevado a cabo,
     * o no (debido a un error), tanto en la wallet y en al bank. Se registra e imprime la orden.
     */
    public Flux<Void> consume2() {

        return receiver.consumeAutoAck(QUEUE_E).flatMap(message -> {

            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            OrderConfirmation orderConfirmation;

            try {
                orderConfirmation = objectMapper.readValue(json, OrderConfirmation.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            return orderRepository.findById(orderConfirmation.getId())
                    .flatMap(order -> {

                        order.setOrderState(orderConfirmation.getOrderState());
                        order.setErrorDescription(orderConfirmation.getErrorDescription());
                        log.info("Order received: " + order);

                        return orderRepository.save(order).then();
                    });
        });
    }


    /**
     * Se crea un mensaje, en el cual se especifica exchange, routing-key y cuerpo del mismo.
     */
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
