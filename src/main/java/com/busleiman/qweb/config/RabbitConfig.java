package com.busleiman.qweb.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import org.modelmapper.ModelMapper;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.*;

import java.util.Map;

import static com.busleiman.qweb.utils.Constants.*;

@Configuration
public class RabbitConfig {

    @Autowired
    AmqpAdmin amqpAdmin;

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    @Bean
    Mono<Connection> connectionMono() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        return Mono.fromCallable(() -> connectionFactory.newConnection("rabbitmq")).cache();
    }

    @Bean
    public ReceiverOptions receiverOptions(Mono<Connection> connectionMono) {
        return new ReceiverOptions()
                .connectionMono(connectionMono);
    }

    @Bean
    Receiver receiver(ReceiverOptions receiverOptions) {
        return RabbitFlux.createReceiver(receiverOptions);
    }

    @Bean
    public SenderOptions senderOptions(Mono<Connection> connectionMono) {
        return new SenderOptions()
                .connectionMono(connectionMono)
                .resourceManagementScheduler(Schedulers.boundedElastic());
    }

    @Bean
    public Sender sender(SenderOptions senderOptions) {
        return RabbitFlux.createSender(senderOptions);
    }

    @PostConstruct
    public void init() {

        // Declare exchanges
        Exchange fanoutExchangeDeclaration = ExchangeBuilder.fanoutExchange(FANOUT_EXCHANGE).build();
        amqpAdmin.declareExchange(fanoutExchangeDeclaration);

        Exchange queuesExchange = ExchangeBuilder.directExchange(QUEUES_EXCHANGE).build();

        amqpAdmin.declareExchange(queuesExchange);

        // Declare queues
        Queue queueA = new Queue(QUEUE_A);
        amqpAdmin.purgeQueue(QUEUE_A, true);

        Queue queueB = new Queue(QUEUE_B);
        amqpAdmin.purgeQueue(QUEUE_B, true);

        Queue queueC = new Queue(QUEUE_C);
        amqpAdmin.purgeQueue(QUEUE_C, true);

        Queue queueD = new Queue(QUEUE_D);
        amqpAdmin.purgeQueue(QUEUE_D, true);

        Queue queueE = new Queue(QUEUE_E);
        amqpAdmin.purgeQueue(QUEUE_E, true);

        Queue queueF = new Queue(QUEUE_F);
        amqpAdmin.purgeQueue(QUEUE_F, true);

        Queue queueG = new Queue(QUEUE_G);
        amqpAdmin.purgeQueue(QUEUE_G, true);

        Queue queueH = new Queue(QUEUE_H);
        amqpAdmin.purgeQueue(QUEUE_H, true);




        amqpAdmin.declareQueue(queueB);
        amqpAdmin.declareQueue(queueC);
        amqpAdmin.declareQueue(queueA);
        amqpAdmin.declareQueue(queueD);
        amqpAdmin.declareQueue(queueE);
        amqpAdmin.declareQueue(queueF);
        amqpAdmin.declareQueue(queueG);
        amqpAdmin.declareQueue(queueH);


        // Declare bindings
        Binding bindingB = BindingBuilder.bind(queueB).to(fanoutExchangeDeclaration).with(QUEUE_B).noargs();
        Binding bindingC = BindingBuilder.bind(queueC).to(fanoutExchangeDeclaration).with(QUEUE_C).noargs();
        Binding bindingA = BindingBuilder.bind(queueA).to(queuesExchange).with(QUEUE_A).noargs();
        Binding bindingD = BindingBuilder.bind(queueD).to(queuesExchange).with(QUEUE_D).noargs();
        Binding bindingE = BindingBuilder.bind(queueE).to(queuesExchange).with(QUEUE_E).noargs();
        Binding bindingF = BindingBuilder.bind(queueF).to(queuesExchange).with(QUEUE_F).noargs();
        Binding bindingG = BindingBuilder.bind(queueG).to(queuesExchange).with(QUEUE_G).noargs();
        Binding bindingH = BindingBuilder.bind(queueH).to(queuesExchange).with(QUEUE_H).noargs();


        amqpAdmin.declareBinding(bindingB);
        amqpAdmin.declareBinding(bindingC);
        amqpAdmin.declareBinding(bindingA);
        amqpAdmin.declareBinding(bindingD);
        amqpAdmin.declareBinding(bindingE);
        amqpAdmin.declareBinding(bindingF);
        amqpAdmin.declareBinding(bindingG);
        amqpAdmin.declareBinding(bindingH);
    }
}