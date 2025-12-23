package com.lld.im.service.group.service;

import com.lld.im.codec.pack.message.ChatMessageAck;

import com.lld.im.common.Constants.Constants;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.enums.command.GroupEventCommand;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.common.model.message.GroupChatMessageContent;
import com.lld.im.common.model.message.MessageContent;

import com.lld.im.common.model.message.OfflineMessageContent;
import com.lld.im.service.group.model.req.SendGroupMessageReq;
import com.lld.im.service.group.mq.GroupChatOperateReceiver;
import com.lld.im.service.message.model.resp.SendMessageResp;
import com.lld.im.service.message.service.CheckSendMessageService;

import com.lld.im.service.message.service.MessageStoreService;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.utils.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @description:
 * @author: lld
 * @version: 1.0
 */
@Service
public class GroupMessageService {
    private static Logger logger = LoggerFactory.getLogger(GroupMessageService.class);

    @Autowired
    CheckSendMessageService checkSendMessageService;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    ImGroupMemberService imGroupMemberService;

    @Autowired
    MessageStoreService messageStoreService;

    @Autowired
    RedisSeq redisSeq;

    private final ThreadPoolExecutor threadPoolExecutor;
    {
        AtomicInteger num = new AtomicInteger(0);
        threadPoolExecutor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS
                , new LinkedBlockingQueue<>(1000), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);//后台线程
                thread.setName("message-group-thread-" + num.getAndIncrement());
                return thread;
            }
        });
    }

    public void process(GroupChatMessageContent messageContent){
        String fromId = messageContent.getFromId();
        String groupId = messageContent.getGroupId();
        Integer appId = messageContent.getAppId();

        GroupChatMessageContent messageFromMessageIdCache = messageStoreService.getMessageFromMessageIdCache(messageContent.getAppId(),
                messageContent.getMessageId(), GroupChatMessageContent.class);
        if(messageFromMessageIdCache != null){
            /// 之前处理过这条消息 重复了 不需要持久化
            threadPoolExecutor.execute(() ->{
                //   1.回ack成功给自己
                ack(messageContent,ResponseVO.successResponse());
                //2.发消息给同步在线端
                syncToSender(messageContent,messageContent);
                //3.发消息给对方在线端
                dispatchMessage(messageContent);
            });
        }

        long seq = redisSeq.doGetSeq(messageContent.getAppId() + ":" + Constants.SeqConstants.GroupMessage
                + messageContent.getGroupId());
        messageContent.setMessageSequence(seq);

        //前置校验
        //这个用户是否被禁言 是否被禁用
        //发送方和接收方是否是好友
        /*ResponseVO responseVO = imServerPermissionCheck(fromId, groupId, appId);
        if(responseVO.isOk()){*/
        ///信息校验移到网关tcp层
            threadPoolExecutor.execute(()->{
                messageStoreService.storeGroupMessage(messageContent);

                List<String> groupMemberId = imGroupMemberService.getGroupMemberId(messageContent.getGroupId(),
                        messageContent.getAppId());
                messageContent.setMemberId(groupMemberId);
                
                OfflineMessageContent offlineMessageContent = new OfflineMessageContent();
                BeanUtils.copyProperties(messageContent,offlineMessageContent);
                offlineMessageContent.setToId(messageContent.getGroupId());
                messageStoreService.storeGroupOfflineMessage(offlineMessageContent,groupMemberId);
                //   1.回ack成功给自己
                ack(messageContent,ResponseVO.successResponse());
                //2.发消息给同步在线端
                syncToSender(messageContent,messageContent);
                //3.发消息给对方在线端
                dispatchMessage(messageContent);

                /// messageId 存储到缓存中
                messageStoreService.setMessageFromMessageIdCache(messageContent.getAppId(),
                        messageContent.getMessageId(),messageContent);
            });
    }

    private void dispatchMessage(GroupChatMessageContent messageContent){
        // 新增：判空校验
        List<String> memberIds = messageContent.getMemberId();
        if (memberIds == null || memberIds.isEmpty()) {
            logger.warn("dispatchMessage 失败：群成员列表为空，groupId={}, appId={}",
                    messageContent.getGroupId(), messageContent.getAppId());
            return;
        }
        for (String memberId : messageContent.getMemberId()) {
            if(!memberId.equals(messageContent.getFromId())){
                messageProducer.sendToUser(memberId,
                        GroupEventCommand.MSG_GROUP,
                        messageContent,messageContent.getAppId());
            }
        }
    }

    private void ack(MessageContent messageContent, ResponseVO responseVO){

        ChatMessageAck chatMessageAck = new ChatMessageAck(messageContent.getMessageId());
        responseVO.setData(chatMessageAck);
        //發消息
        messageProducer.sendToUser(messageContent.getFromId(),
                GroupEventCommand.GROUP_MSG_ACK,
                responseVO,messageContent
        );
    }

    private void syncToSender(GroupChatMessageContent messageContent, ClientInfo clientInfo){
        messageProducer.sendToUserExceptClient(messageContent.getFromId(),
                GroupEventCommand.MSG_GROUP,messageContent,messageContent);
    }

    private ResponseVO imServerPermissionCheck(String fromId, String toId, Integer appId){
        ResponseVO responseVO = checkSendMessageService
                .checkGroupMessage(fromId, toId,appId);
        return responseVO;
    }


    public SendMessageResp send(SendGroupMessageReq req) {

        SendMessageResp sendMessageResp = new SendMessageResp();
        GroupChatMessageContent message = new GroupChatMessageContent();
        BeanUtils.copyProperties(req,message);

        messageStoreService.storeGroupMessage(message);

        sendMessageResp.setMessageKey(message.getMessageKey());
        sendMessageResp.setMessageTime(System.currentTimeMillis());
        //2.发消息给同步在线端
        syncToSender(message,message);
        //3.发消息给对方在线端
        dispatchMessage(message);

        return sendMessageResp;

    }
}
