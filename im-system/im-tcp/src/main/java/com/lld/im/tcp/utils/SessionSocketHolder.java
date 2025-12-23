package com.lld.im.tcp.utils;


import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.pack.user.UserStatusChangeNotifyPack;
import com.lld.im.codec.proto.MessageHeader;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.enums.ImConnectStatusEnum;
import com.lld.im.common.enums.command.UserEventCommand;
import com.lld.im.common.model.UserClientDto;
import com.lld.im.common.model.UserSession;
import com.lld.im.tcp.publish.MqMessageProducer;
import com.lld.im.tcp.redis.RedisManager;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
//该类主要负责维护用户客户端信息（包括应用 ID、用户 ID、客户端类型、设备标识 IMEI ）
//        与对应的 NioSocketChannel（Netty 用于网络通信的通道对象）之间的映射关系，
//        以及处理用户会话相关的增删改操作，同时在用户下线时进行相关状态更新和消息通知。
//管理channel
public class SessionSocketHolder {

    private static final Map<UserClientDto, NioSocketChannel> CHANNELS = new ConcurrentHashMap<>();

    public static void put(Integer appId,String userId,Integer clientType,
                           String imei
            , NioSocketChannel channel) {
        UserClientDto dto = new UserClientDto();
        dto.setImei(imei);
        dto.setAppId(appId);
        dto.setClientType(clientType);
        dto.setUserId(userId);
        CHANNELS.put(dto, channel);
    }

    public static NioSocketChannel get(Integer appId,String userId,
                                       Integer clientType,String imei){
        UserClientDto dto = new UserClientDto();
        dto.setImei(imei);
        dto.setAppId(appId);
        dto.setClientType(clientType);
        dto.setUserId(userId);
        return CHANNELS.get(dto);
    }

    //获取对应用户的所有channels
    public static List<NioSocketChannel> get(Integer appId , String id) {

        Set<UserClientDto> channelInfos = CHANNELS.keySet();
        List<NioSocketChannel> channels = new ArrayList<>();

        channelInfos.forEach(channel ->{
            if(channel.getAppId().equals(appId) && id.equals(channel.getUserId())){
                channels.add(CHANNELS.get(channel));
            }
        });

        return channels;
    }

    public static void remove(Integer appId,String userId,Integer clientType,String imei){
        UserClientDto dto = new UserClientDto();
        dto.setAppId(appId);
        dto.setImei(imei);
        dto.setUserId(userId);
        dto.setClientType(clientType);
        CHANNELS.remove(dto);
    }

    public static void remove(NioSocketChannel channel){
        // 1. 遍历 CHANNELS 中所有的键值对（entrySet() 获取所有条目）
        CHANNELS.entrySet().stream()
                // 2. 过滤：只保留“值（Value）等于传入的 channel 通道”的条目
                .filter(entity -> entity.getValue() == channel)
                // 3. 对过滤后的每个条目，从 CHANNELS 中删除其对应的键（Key）
                .forEach(entry -> CHANNELS.remove(entry.getKey()));
    }

    public static void removeUserSession(NioSocketChannel nioSocketChannel){
        //删除 session
        //redis 删除
        String userId = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get();
        Integer appId = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.AppId)).get();
        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
        String imei = (String) nioSocketChannel
                .attr(AttributeKey.valueOf(Constants.Imei)).get();

        SessionSocketHolder.remove(appId,userId,clientType,imei);
        RedissonClient redissonClient = RedisManager.getRedissonClient();
        RMap<Object, Object> map = redissonClient.getMap(appId +
                Constants.RedisConstants.UserSessionConstants + userId);
        map.remove(clientType+":"+imei);

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setAppId(appId);
        messageHeader.setImei(imei);
        messageHeader.setClientType(clientType);

        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        userStatusChangeNotifyPack.setAppId(appId);
        userStatusChangeNotifyPack.setUserId(userId);
        userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
        MqMessageProducer.sendMessage(userStatusChangeNotifyPack,messageHeader, UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());


        nioSocketChannel.close();
    }

    //用户离线逻辑 只删除session 不删除redis中的会话 修改redis中的状态
    public static void offlineUserSession(NioSocketChannel nioSocketChannel){
        String userId = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get();
        Integer appId = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.AppId)).get();
        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
        String imei = (String) nioSocketChannel
                .attr(AttributeKey.valueOf(Constants.Imei)).get();
        SessionSocketHolder.remove(appId,userId,clientType,imei);

        RedissonClient redissonClient = RedisManager.getRedissonClient();
        RMap<String, String> map = redissonClient.getMap(appId +
                Constants.RedisConstants.UserSessionConstants + userId);
        String sessionStr = map.get(clientType.toString()+":" + imei);

        if(!StringUtils.isBlank(sessionStr)){
            UserSession userSession = JSONObject.parseObject(sessionStr, UserSession.class);
            userSession.setConnectState(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
            map.put(clientType.toString()+":"+imei,JSONObject.toJSONString(userSession));
        }
        //通知：发消息给逻辑层
        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setAppId(appId);
        messageHeader.setImei(imei);
        messageHeader.setClientType(clientType);

        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        userStatusChangeNotifyPack.setAppId(appId);
        userStatusChangeNotifyPack.setUserId(userId);
        userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
        MqMessageProducer.sendMessage(userStatusChangeNotifyPack,messageHeader, UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());

        nioSocketChannel.close();
    }
}
