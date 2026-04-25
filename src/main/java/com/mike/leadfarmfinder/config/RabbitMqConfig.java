package com.mike.leadfarmfinder.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {

    private final LeadFinderRabbitProperties rabbitProperties;

    @Bean
    public DirectExchange outreachEventsExchange() {
        return new DirectExchange(rabbitProperties.getOutreachEventsExchange(), true, false);
    }

    @Bean
    public Queue outreachEventsQueue() {
        return QueueBuilder
                .durable(rabbitProperties.getOutreachEventsQueue())
                .withArgument("x-dead-letter-exchange", rabbitProperties.getOutreachEventsExchange())
                .withArgument("x-dead-letter-routing-key", rabbitProperties.getOutreachEventsRetryRoutingKey())
                .build();
    }

    @Bean
    public Queue outreachEventsRetryQueue() {
        return QueueBuilder
                .durable(rabbitProperties.getOutreachEventsRetryQueue())
                .withArgument("x-message-ttl", rabbitProperties.getRetryTtlMs())
                .withArgument("x-dead-letter-exchange", rabbitProperties.getOutreachEventsExchange())
                .withArgument("x-dead-letter-routing-key", rabbitProperties.getOutreachEventsRoutingKey())
                .build();
    }

    @Bean
    public Queue outreachEventsDlq() {
        return QueueBuilder
                .durable(rabbitProperties.getOutreachEventsDlq())
                .build();
    }

    @Bean
    public Binding outreachEventsBinding() {
        return BindingBuilder
                .bind(outreachEventsQueue())
                .to(outreachEventsExchange())
                .with(rabbitProperties.getOutreachEventsRoutingKey());
    }

    @Bean
    public Binding outreachEventsRetryBinding() {
        return BindingBuilder
                .bind(outreachEventsRetryQueue())
                .to(outreachEventsExchange())
                .with(rabbitProperties.getOutreachEventsRetryRoutingKey());
    }

    @Bean
    public Binding outreachEventsDlqBinding() {
        return BindingBuilder
                .bind(outreachEventsDlq())
                .to(outreachEventsExchange())
                .with(rabbitProperties.getOutreachEventsDlqRoutingKey());
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}