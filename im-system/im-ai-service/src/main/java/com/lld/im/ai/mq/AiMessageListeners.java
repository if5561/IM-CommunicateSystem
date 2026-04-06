package com.lld.im.ai.mq;

import com.lld.im.ai.service.MessageIngestionService;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AiMessageListeners {

    private static final String STORE_P2P_EXCHANGE = "storeP2PMessage";

    private static final String STORE_GROUP_EXCHANGE = "storeGroupMessage";

    private final MessageIngestionService messageIngestionService;

    public AiMessageListeners(MessageIngestionService messageIngestionService) {
        this.messageIngestionService = messageIngestionService;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${im.ai.p2p-queue}", durable = "true"),
            exchange = @Exchange(value = STORE_P2P_EXCHANGE, durable = "true"),
            key = ""
    ))
    public void onP2PMessage(String payload) throws Exception {
        messageIngestionService.ingestP2P(payload);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${im.ai.group-queue}", durable = "true"),
            exchange = @Exchange(value = STORE_GROUP_EXCHANGE, durable = "true"),
            key = ""
    ))
    public void onGroupMessage(String payload) throws Exception {
        messageIngestionService.ingestGroup(payload);
    }
}
