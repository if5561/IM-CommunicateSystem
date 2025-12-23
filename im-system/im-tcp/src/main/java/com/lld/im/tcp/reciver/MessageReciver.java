package com.lld.im.tcp.reciver;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.proto.MessagePack;
import com.lld.im.common.Constants.Constants;
import com.lld.im.tcp.reciver.process.BaseProcess;
import com.lld.im.tcp.reciver.process.ProcessFactory;
import com.lld.im.tcp.utils.MqFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;

//监听消息
@Slf4j
public class MessageReciver {

    private static String brokerId;

    private static void startReciverMessage(){
        try{
            Channel channel = MqFactory.getChannel(Constants.RabbitConstants.MessageService2Im + brokerId);
            channel.queueDeclare(Constants.RabbitConstants.MessageService2Im + brokerId,true,false,
                    false,null);//channel绑定队列
            channel.queueBind(Constants.RabbitConstants.MessageService2Im + brokerId,
                    Constants.RabbitConstants.MessageService2Im, brokerId);//绑定交换机
            channel.basicConsume(Constants.RabbitConstants.MessageService2Im + brokerId,false,
                    new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            //todo 处理消息服务发来的消息
                            try {
                                String msgStr = new String(body);
                                MessagePack messagePack = JSONObject.parseObject(msgStr, MessagePack.class);
                                BaseProcess messageProcess = ProcessFactory.getMessageProcess(messagePack.getCommand());
                                messageProcess.process(messagePack);
                                log.info(msgStr);
                                //第一个参数使标记符，第二个参数是：是否将标记符之前的消息全部提交
                                //此方法用于向 RabbitMQ 服务器告知消费者已成功处理某条消息，服务器接收到该确认后，就会将这条消息从队列里移除。
                                channel.basicAck(envelope.getDeliveryTag(), false);
                            }catch (Exception e){
                                e.printStackTrace();
                                channel.basicNack(envelope.getDeliveryTag(), false,false);
                            }
                        }//重写收到消息的方法
                    });
        }catch (Exception e){

        }
    }

    public static void init(){
        startReciverMessage();
    }

    public static void init(String brokerId){
        if(StringUtils.isBlank(MessageReciver.brokerId)){
            MessageReciver.brokerId=brokerId;
        }
        startReciverMessage();
    }
}
