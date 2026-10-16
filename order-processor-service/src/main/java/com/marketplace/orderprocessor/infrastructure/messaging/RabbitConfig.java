package com.marketplace.orderprocessor.infrastructure.messaging;

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
 * Topologia das filas do Order Processor.
 *
 * <p>As declaracoes sao identicas as do Checkout e idempotentes no broker, o que permite subir os
 * servicos em qualquer ordem: o primeiro a iniciar cria as filas.</p>
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
