package com.lld.im.tcp.reciver.process;

import com.lld.im.codec.proto.MessagePack;
import com.lld.im.tcp.utils.SessionSocketHolder;
import io.netty.channel.socket.nio.NioSocketChannel;

public abstract class BaseProcess {
    public abstract void processBefore();
    //处理前

    public void process(MessagePack messagePack){
        processBefore();
        //处理消息
        NioSocketChannel channel = SessionSocketHolder.get(messagePack.getAppId(),
                messagePack.getToId(), messagePack.getClientType(),
                messagePack.getImei());
        if(channel!=null){
            channel.writeAndFlush(messagePack);//发送消息
        }
        processAfter();
    }

    public abstract void processAfter();
    //处理后
}
