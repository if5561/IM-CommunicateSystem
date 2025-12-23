package com.lld.im.tcp.utils;

import com.lld.im.codec.config.BootstrapConfig;
import com.lld.im.common.Constants.Constants;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.val;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * @description:
 * @author: lld
 * @version: 1.0
 */
public class MqFactory {

    private static ConnectionFactory factory = null;

    private static Channel defaultChannel;

    private static ConcurrentHashMap<String, Channel> channelMap = new ConcurrentHashMap<> ();

    private static Connection getConnection () throws IOException, TimeoutException {
        Connection connection = factory.newConnection ();
        return connection;
    }

    public static Channel getChannel (String channelName) throws IOException, TimeoutException {
        Channel channel = channelMap.get (channelName);
        if (channel == null) {
            channel = getConnection ().createChannel ();
            channelMap.put (channelName, channel);
        }
        return channel;
    }

    public static void init (BootstrapConfig.Rabbitmq rabbitmq) {
        if (factory == null) {
            factory = new ConnectionFactory ();
            factory.setHost (rabbitmq.getHost ());
            factory.setPort (rabbitmq.getPort ());
            factory.setUsername (rabbitmq.getUserName ());
            factory.setPassword (rabbitmq.getPassword ());
            factory.setVirtualHost (rabbitmq.getVirtualHost ());//设置权限
        }
    }

}




