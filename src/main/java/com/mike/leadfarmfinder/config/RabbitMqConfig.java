package com.mike.leadfarmfinder.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {

    private final LeadFinderRabbitProperties rabbitProperties;

    @Bean
    public DirectExchange outreachEventsExchange() {
        return new DirectExchange(rabbitProperties.getOutreachEventsExchange(), true, false);
    }

    @Bean
    public Queue outreachEventsDlq() {
        return QueueBuilder
                .durable(rabbitProperties.getOutreachEventsDlq())
                .build();
    }

    @Bean
    public Queue outreachEventsQueue() {
        return QueueBuilder
                .durable(rabbitProperties.getOutreachEventsQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(rabbitProperties.getOutreachEventsDlq())
                .build();
    }

    @Bean
    public Binding outreachEventsBinding(Queue outreachEventsQueue, DirectExchange outreachEventsExchange) {
        return BindingBuilder
                .bind(outreachEventsQueue)
                .to(outreachEventsExchange)
                .with(rabbitProperties.getOutreachEventsRoutingKey());
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
