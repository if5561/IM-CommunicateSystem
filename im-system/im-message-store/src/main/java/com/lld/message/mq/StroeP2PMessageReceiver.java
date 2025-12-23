package com.lld.message.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.enums.command.MessageCommand;
import com.lld.message.model.DoStoreP2PMessageDto;
import com.lld.im.common.model.message.MessageContent;
import com.lld.message.dao.ImMessageBodyEntity;
import com.lld.message.service.StoreMessageService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StroeP2PMessageReceiver {
    private static Logger logger = LoggerFactory.getLogger(StroeP2PMessageReceiver.class);

    @Autowired
    StoreMessageService storeMessageService;

    //@Payload Message message：这是一个来自 RabbitMQ 消息队列的消息对象，@Payload 注解用于标识该参数是消息的负载部分，即消息的实际内容。
//@Headers Map<String, Object> headers：这是消息的头部信息，以键值对的形式存储，@Headers 注解用于标识该参数是消息的头部。
//Channel channel：这是 RabbitMQ 的通道对象，用于与 RabbitMQ 服务器进行交互，例如发送确认或否定确认消息。
    @RabbitListener(
            //注解的方式绑定队列
            bindings = @QueueBinding(   //设置队列名称  交换机
                    value = @Queue(value = Constants.RabbitConstants.StoreP2PMessage,durable = "true"),
                    exchange = @Exchange(value = Constants.RabbitConstants.StoreP2PMessage,durable = "true")
            ),concurrency = "10"    //每次拉取的消息条数
    )
    public void onChatMessage(@Payload Message message,
                              @Headers Map<String,Object> headers, Channel channel) throws Exception {
        //监听队列
        String msg = new String(message.getBody(),"utf-8");
        //打印收到的消息   tcp发送给逻辑层
        logger.info("CHAT MSG FROM QUEUE ::: {}",msg);
        Long deliveryTag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);
        try {
            JSONObject jsonObject = JSON.parseObject(msg);

            DoStoreP2PMessageDto doStoreP2PMessageDto = jsonObject.toJavaObject(DoStoreP2PMessageDto.class);
            ImMessageBodyEntity messageBody = jsonObject.getObject("messageBody", ImMessageBodyEntity.class);
            doStoreP2PMessageDto.setImMessageBodyEntity(messageBody);
            storeMessageService.doStoreP2PMessage(doStoreP2PMessageDto);

            channel.basicAck(deliveryTag, false);
        }catch (Exception e){
            logger.error("处理消息出现异常：{}", e.getMessage());
            logger.error("RMQ_CHAT_TRAN_ERROR", e);
            logger.error("NACK_MSG:{}", msg);
            //第一个false 表示不批量拒绝，第二个false表示不重回队列
            channel.basicNack(deliveryTag, false, false);
        }

    }
}
