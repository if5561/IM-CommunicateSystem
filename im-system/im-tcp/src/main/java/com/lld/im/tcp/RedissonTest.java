package com.lld.im.tcp;

import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;

public class RedissonTest {
    public static void main(String[] args) {

        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");//单机模式 配置地址
        StringCodec stringCodec = new StringCodec();//设置解码器
        config.setCodec(stringCodec);
        RedissonClient redissonClient = Redisson.create(config);//创建redisson客户端

        /*//获取一个key 和value
        RBucket<Object> im = redissonClient.getBucket("im");//key
        System.out.println(im.get());//获取value
        im.set("im");
        System.out.println(im.get());//再次获取value*/

        /*RMap<String, String> imMap = redissonClient.getMap("imMap");//返回一个map
        String client = imMap.get("client");
        System.out.println(client);
        imMap.put("client","webclient");
        System.out.println(imMap.get("client"));*/

        //发布订阅 redis不能持久化
        //redis的发送订阅 会发送给所有监听这个topic的客户端
        RTopic topic = redissonClient.getTopic("topic");
        topic.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence charSequence, String s) {
                //写收到消息后的逻辑 .....
                System.out.println("client1收到消息： "+ s);
            }
        });//设置监听
        RTopic topic2 = redissonClient.getTopic("topic");
        topic2.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence charSequence, String s) {
                //写收到消息后的逻辑 .....
                System.out.println("client2收到消息： "+ s);
            }
        });//设置监听
        RTopic topic3 = redissonClient.getTopic("topic");
        topic3.publish("hello");
    }
}
