package com.marketplace.checkout.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Topologia das filas do Checkout.
 *
 * <p>As mensagens sao publicadas pelo exchange padrao usando o nome da fila como routing key.
 * E a forma mais direta de ler o fluxo da SAGA no painel do RabbitMQ, sem exchanges e bindings
 * intermediarios. As filas sao duraveis para sobreviver a reinicios do broker.</p>
 */
@Configuration
@Profile("!test")
public class RabbitConfig {

    @Bean
    Queue orderCreatedQueue() {
        return QueueBuilder.durable(QueueNames.ORDER_CREATED).build();
    }

    @Bean
    Queue orderCompensationQueue() {
        return QueueBuilder.durable(QueueNames.ORDER_COMPENSATION).build();
    }

    @Bean
    Queue orderStatusChangedQueue() {
        return QueueBuilder.durable(QueueNames.ORDER_STATUS_CHANGED).build();
    }

    /**
     * Reaproveita o {@link ObjectMapper} da aplicacao para que as mensagens usem a mesma
     * convencao snake_case do contrato HTTP e saibam serializar {@code LocalDateTime}.
     */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
