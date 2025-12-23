package com.lld.im.service.message.service;

import com.lld.im.codec.pack.message.ChatMessageAck;
import com.lld.im.codec.pack.message.MessageReciveServerAckPack;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.enums.ConversationTypeEnum;
import com.lld.im.common.enums.command.MessageCommand;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.common.model.message.MessageContent;
import com.lld.im.common.model.message.MessageReciveAckContent;
import com.lld.im.common.model.message.OfflineMessageContent;
import com.lld.im.service.message.model.req.SendMessageReq;
import com.lld.im.service.message.model.resp.SendMessageResp;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.utils.ConversationIdGenerate;
import com.lld.im.service.utils.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class P2PMessageService {
    private static Logger logger = LoggerFactory.getLogger(P2PMessageService.class);

    @Autowired
    CheckSendMessageService checkSendMessageService;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    MessageStoreService messageStoreService;

    @Autowired
    RedisSeq redisSeq;

    //代码块初始化
    private final ThreadPoolExecutor threadPoolExecutor;
    {
        final AtomicInteger num = new AtomicInteger(0);
        threadPoolExecutor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);//后台线程
                thread.setName("message-process-thread-" + num.getAndIncrement());
                return thread;
            }
        });

    }

    public void process(MessageContent messageContent){
        //用户发消息逻辑

        String fromId = messageContent.getFromId();
        String toId = messageContent.getToId();
        Integer appId = messageContent.getAppId();

        /// 用messageId 从缓存中获取消息
        MessageContent messageFromMessageIdCache = messageStoreService.getMessageFromMessageIdCache
                (messageContent.getAppId(), messageContent.getMessageId(),MessageContent.class);
        if (messageFromMessageIdCache!=null){
            //不需要持久化 已经重复了 直接分发即可
            threadPoolExecutor.execute(() ->{
                ack(messageContent,ResponseVO.successResponse());
                //2.发消息给同步在线端
                syncToSender(messageFromMessageIdCache,messageFromMessageIdCache);
                //3.发消息给对方在线端
                List<ClientInfo> clientInfos = dispatchMessage(messageFromMessageIdCache);
                if(clientInfos.isEmpty()){
                    //发送接收确认给发送方，要带上是服务端发送的标识
                    revicerAck(messageFromMessageIdCache);
                }
            });
            return;
        }

        //appId + Seq + （from + to）
        long seq = redisSeq.doGetSeq(messageContent.getAppId() + ":"
                +  Constants.SeqConstants.Message + ":" +
                ConversationIdGenerate.generateP2PId(messageContent.getFromId(),
                        messageContent.getToId()));
        messageContent.setMessageSequence(seq);

        //前置校验
        //这个用户是否被禁言
        //发送方和接收方是否是好友 （不是好友也可以发信息 用配置文件标志这个开关）
        //ResponseVO responseVO = imServerPermissionCheck(fromId, toId, appId);
        //if(responseVO.isOk()){
            threadPoolExecutor.execute(()->{
                messageStoreService.storeP2PMessage(messageContent);

                //TODO ack之前插入离线消息
                OfflineMessageContent offlineMessageContent = new OfflineMessageContent();
                BeanUtils.copyProperties(messageContent,offlineMessageContent);
                offlineMessageContent.setConversationType(ConversationTypeEnum.P2P.getCode());
                messageStoreService.storeOfflineMessage(offlineMessageContent);
                //回ack成功给自己
                ack(messageContent,ResponseVO.successResponse());
                //2.发消息给同步在线端
                syncToSender(messageContent,messageContent);
                //3.发消息给对方在线端
                List<ClientInfo> clientInfos = dispatchMessage(messageContent);

                ///将messageId 存到缓存中
                messageStoreService.setMessageFromMessageIdCache(messageContent.getAppId(),
                        messageContent.getMessageId(),messageContent);

                if(clientInfos.isEmpty()){
                    //发送接收确认给发送方 要带上是服务端发送的标识 因为要发送给的人（另一个客户端）离线了。
                    revicerAck(messageContent);
                }
            });///成功的任务提交到线程池
        /*}else{
            //告诉客户端 失败了 ack
            ack(messageContent,responseVO);
        }*/

    }

    //分发消息
    private List<ClientInfo> dispatchMessage(MessageContent messageContent){
        List<ClientInfo> clientInfos = messageProducer.sendToUser(messageContent.getToId(),
                MessageCommand.MSG_P2P, messageContent, messageContent.getAppId());
        return clientInfos;
    }

    private void ack(MessageContent messageContent,ResponseVO responseVO){
        logger.info("msg ack,msgId={},checkResult{}",messageContent.getMessageId(),
                responseVO.getCode());
        ChatMessageAck chatMessageAck = new ChatMessageAck(messageContent.getMessageId(),
                messageContent.getMessageSequence());
        responseVO.setData(chatMessageAck);//messageId 表示这条消息已经收到了
        //发消息
        messageProducer.sendToUser(messageContent.getFromId(),
                MessageCommand.MSG_ACK, responseVO,messageContent);
                //发送给发送方（fromId）
    }

    public void revicerAck(MessageContent messageContent){
        MessageReciveServerAckPack pack = new MessageReciveServerAckPack();
        pack.setFromId(messageContent.getToId());
        pack.setToId(messageContent.getFromId());
        pack.setMessageKey(messageContent.getMessageKey());
        pack.setMessageSequence(messageContent.getMessageSequence());
        pack.setServerSend(true);
        messageProducer.sendToUser(messageContent.getFromId(),MessageCommand.MSG_RECIVE_ACK,
                pack,new ClientInfo(messageContent.getAppId(),messageContent.getClientType()
                        ,messageContent.getImei()));
    }

    //发消息给同步在线端
    private void syncToSender(MessageContent messageContent, ClientInfo clientInfo){
        messageProducer.sendToUserExceptClient(messageContent.getFromId(),
                MessageCommand.MSG_P2P,messageContent,messageContent);
    }


    //校验用户和关系
    public ResponseVO imServerPermissionCheck(String fromId,String toId,
                                               Integer appId){
        ResponseVO responseVO = checkSendMessageService.checkSenderForvidAndMute(fromId, appId);
        if(!responseVO.isOk()){
            return responseVO;
        }
        responseVO = checkSendMessageService.checkFriendShip(fromId, toId, appId);
        return responseVO;

    }

    public SendMessageResp send(SendMessageReq req) {
        SendMessageResp sendMessageResp = new SendMessageResp();
        MessageContent message = new MessageContent();
        BeanUtils.copyProperties(req,message);
        //插入数据
        messageStoreService.storeP2PMessage(message);
        sendMessageResp.setMessageKey(message.getMessageKey());
        sendMessageResp.setMessageTime(System.currentTimeMillis());

        //2.发消息给同步在线端
        syncToSender(message,message);
        //3.发消息给对方在线端
        dispatchMessage(message);
        return sendMessageResp;
    }
}
