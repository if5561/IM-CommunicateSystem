package com.lld.im.service.group.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.enums.command.GroupEventCommand;
import com.lld.im.common.enums.command.MessageCommand;
import com.lld.im.common.model.message.GroupChatMessageContent;

import com.lld.im.common.model.message.MessageReadedContent;
import com.lld.im.common.model.message.MessageReciveAckContent;
import com.lld.im.service.group.service.GroupMessageService;
import com.lld.im.service.message.service.MessageSyncService;
import com.lld.im.service.message.service.P2PMessageService;
import com.lld.im.service.user.controller.ImUserDataController;
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

import java.util.Map;

public class GroupChatOperateReceiver {
    private static Logger logger = LoggerFactory.getLogger(GroupChatOperateReceiver.class);
    @Autowired
    GroupMessageService groupMessageService;

    @Autowired
    MessageSyncService messageSyncService;




    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = Constants.RabbitConstants.Im2GroupService,durable = "true"),
                    exchange = @Exchange(value = Constants.RabbitConstants.Im2GroupService,durable = "true")
            ),concurrency = "10"
    )
    public void onChatMessage(@Payload Message message,
                              @Headers Map<String,Object> headers,
                              Channel channel) throws Exception {
        String msg = new String(message.getBody(),"utf-8");
        logger.info("CHAT MSG FORM QUEUE ::: {}", msg);
        Long deliveryTag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);
        try {
            JSONObject jsonObject = JSON.parseObject(msg);
            Integer command = jsonObject.getInteger("command");
            if(command.equals(GroupEventCommand.MSG_GROUP.getCommand())){
                //处理消息
                GroupChatMessageContent messageContent
                        = jsonObject.toJavaObject(GroupChatMessageContent.class);
                //             p2PMessageService.process(messageContent);
                groupMessageService.process(messageContent);
            }else if(command.equals(MessageCommand.MSG_RECIVE_ACK.getCommand())){
                //消息接收确认
                MessageReciveAckContent messageContent
                        = jsonObject.toJavaObject(MessageReciveAckContent.class);
                messageSyncService.receiveMark(messageContent);
            }else if(command.equals(MessageCommand.MSG_READED.getCommand())){
                //消息接收确认
                MessageReadedContent messageContent
                        = jsonObject.toJavaObject(MessageReadedContent.class);
                messageSyncService.readMark(messageContent);
            }else if (command.equals(GroupEventCommand.MSG_GROUP_READED.getCommand())) {
                MessageReadedContent messageReaded = JSON.parseObject(msg, new TypeReference<MessageReadedContent>() {
                }.getType());
                messageSyncService.groupReadMark(messageReaded);
            }
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
