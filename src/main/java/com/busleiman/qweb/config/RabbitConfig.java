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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.*;

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
        return Mono.fromCallable(() -> connectionFactory.newConnection("http://localhost:15672/#/nodes/rabbit%40C11-P31HQN68UOB")).cache();
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
        String topicExchange = "topic-exchange";
        String routingKey = "topic.key";

        // Declare exchanges
        Exchange topicExchangeDeclaration = ExchangeBuilder.fanoutExchange(topicExchange).build();
        amqpAdmin.declareExchange(topicExchangeDeclaration);

        Exchange directExchangeA = ExchangeBuilder.directExchange("queue-A").build();
        Exchange directExchangeD = ExchangeBuilder.directExchange("queue-D").build();
        Exchange directExchangeE = ExchangeBuilder.directExchange("queue-E").build();
        Exchange directExchangeX = ExchangeBuilder.directExchange("queue-X").build();
        Exchange directExchangeF = ExchangeBuilder.directExchange("queue-F").build();

        amqpAdmin.declareExchange(directExchangeA);
        amqpAdmin.declareExchange(directExchangeD);
        amqpAdmin.declareExchange(directExchangeE);
        amqpAdmin.declareExchange(directExchangeX);
        amqpAdmin.declareExchange(directExchangeF);

        // Declare queues
        Queue queueB = new Queue("queue-B");
        Queue queueC = new Queue("queue-C");
        Queue queueA = new Queue("queue-A");
        Queue queueD = new Queue("queue-D");
        Queue queueE = new Queue("queue-E");
        Queue queueX = new Queue("queue-X");
        Queue queueF = new Queue("queue-F");

        amqpAdmin.declareQueue(queueB);
        amqpAdmin.declareQueue(queueC);
        amqpAdmin.declareQueue(queueA);
        amqpAdmin.declareQueue(queueD);
        amqpAdmin.declareQueue(queueE);
        amqpAdmin.declareQueue(queueX);
        amqpAdmin.declareQueue(queueF);

        // Declare bindings
        Binding bindingB = BindingBuilder.bind(queueB).to(topicExchangeDeclaration).with(routingKey).noargs();
        Binding bindingC = BindingBuilder.bind(queueC).to(topicExchangeDeclaration).with(routingKey).noargs();
        Binding bindingA = BindingBuilder.bind(queueA).to(directExchangeA).with(routingKey).noargs();
        Binding bindingD = BindingBuilder.bind(queueD).to(directExchangeD).with(routingKey).noargs();
        Binding bindingE = BindingBuilder.bind(queueE).to(directExchangeE).with(routingKey).noargs();
        Binding bindingX = BindingBuilder.bind(queueX).to(directExchangeX).with(routingKey).noargs();
        Binding bindingF = BindingBuilder.bind(queueF).to(directExchangeF).with(routingKey).noargs();

        amqpAdmin.declareBinding(bindingB);
        amqpAdmin.declareBinding(bindingC);
        amqpAdmin.declareBinding(bindingA);
        amqpAdmin.declareBinding(bindingD);
        amqpAdmin.declareBinding(bindingE);
        amqpAdmin.declareBinding(bindingX);
        amqpAdmin.declareBinding(bindingF);
    }
}