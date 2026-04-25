package com.mike.leadfarmfinder.service.outreach.event;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RabbitRetrySupport {

    public long getRetryCount(Message message, String retryQueueName) {
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");

        if (!(xDeath instanceof List<?> list)) {
            return 0;
        }

        for (Object entry : list) {
            Map<String, Object> map = (Map<String, Object>) entry;

            if (retryQueueName.equals(map.get("queue"))) {
                return ((Number) map.get("count")).longValue();
            }
        }

        return 0;
    }
}