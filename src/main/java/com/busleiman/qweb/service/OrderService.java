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

        //Se envía una orden que va a fallar debido a fondos insuficientes.
        Order order1 = Order.builder()
                .buyerDni("42384769")
                .usdAmount(1000000.0)
                .javaCoinPrice(500.0)
                .bankAccepted(OrderState.IN_PROGRESS)
                .walletAccepted(OrderState.IN_PROGRESS)
                .orderState(OrderState.IN_PROGRESS)
                .build();
        send(order1).subscribe();

        //Se envía una orden que va a ser aceptada.
        Order order2 = Order.builder()
                .buyerDni("42384769")
                .usdAmount(100.0)
                .javaCoinPrice(500.0)
                .bankAccepted(OrderState.IN_PROGRESS)
                .walletAccepted(OrderState.IN_PROGRESS)
                .orderState(OrderState.IN_PROGRESS)
                .build();
        send(order2).subscribe();

        consume().subscribe();
        consume2().subscribe();
    }


    @PreDestroy
    public void close() throws Exception {
        connectionMono.block().close();
    }

    /**
     * Se envía la orden a registrar mediante una exchange fanout,
     * sera consumida por el servicio Bank, que validara y descontara el saldo del usuario,
     * y por la Wallet, que valida la existencia del usuario, y si no lo crea.
     */
    public Mono<Void> send(Order order) {
        order.calculateJavaCoinsAmount();

        return orderRepository.save(order)
                .map(order1 -> modelMapper.map(order1, OrderRequest.class))
                .flatMap(orderRequest -> sender.send(outboundMessage(orderRequest, "", FANOUT_EXCHANGE)));
    }


    /**
     * Se recibe el mensaje order confirmation tanto por parte del servicio Wallet como del servicio Bank,
     * se registra si el mismo aceptó o no la operación.
     * <p>
     * En el caso de no haberse recibido respuesta del otro servicio,
     * se guarda y se espera la confirmación del mismo.
     * <p>
     * En el caso de que ambos microservicios acepten la orden se envía un mensaje con los datos de un
     * vendedor que toma la orden.
     * <p>
     * En el caso de que ambos servicios respondieron, y uno no acepto la orden,
     * se envía el mensaje de error a los servicios para mantener consistencia.
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

                        if (order.getWalletAccepted().equals(OrderState.IN_PROGRESS) || order.getBankAccepted().equals(OrderState.IN_PROGRESS)) {
                            //Una de las dos partes está en in progress
                            if (orderState.equals(OrderState.NOT_ACCEPTED)) {
                                order.setErrorDescription(orderConfirmation.getErrorDescription());
                                order.setOrderState(OrderState.NOT_ACCEPTED);
                            }
                             return orderRepository.save(order).then(Mono.empty());
                        } else if (order.getWalletAccepted().equals(OrderState.ACCEPTED) && order.getBankAccepted().equals(OrderState.ACCEPTED)) {
                            //Orden aceptada y tomada por un vendedor
                            orderConfirmation.setSellerDni("46171291");
                            order.setSellerDni("46171291");

                            orderConfirmation.setOrderState(OrderState.ACCEPTED);

                            return orderRepository.save(order).thenReturn(orderConfirmation);
                        } else {
                            //Una de las dos partes no acepto la orden
                            orderConfirmation.setErrorDescription(order.getErrorDescription() != null ? order.getErrorDescription() : orderConfirmation.getErrorDescription());
                            orderConfirmation.setOrderState(OrderState.NOT_ACCEPTED);
                            orderConfirmation.setSellerDni("");

                            return orderRepository.save(order).thenReturn(orderConfirmation);
                        }
                    });
        }).flatMap(confirmation -> sender.send(outboundMessage(confirmation, QUEUE_A, QUEUES_EXCHANGE)));
    }

    /**
     * Se recibe el mensaje order confirmation final luego de que la transacción se haya llevado a cabo,
     * o no (debido a un error), tanto en la wallet y en el bank. Se registra e imprime la orden.
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
