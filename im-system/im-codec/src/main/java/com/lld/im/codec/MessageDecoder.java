package com.lld.im.codec;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.proto.Message;
import com.lld.im.codec.proto.MessageHeader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * 消息解码类
 */
public class MessageDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        //请求头（指令
        // 版本
        // clientType
        // 消息解析类型
        // appId
        // imei长度
        // bodylen）+ imei号 + 请求体

        if(in.readableBytes() < 28){
            return;
        }
        //读取数据
        /**获取command*/
        int command = in.readInt();

        /**获取version*/
        int version = in.readInt();

        /**获取clientType*/
        int clientType = in.readInt();

        /**获取messageType*/
        int messageType = in.readInt();

        /**获取appId*/
        int appId = in.readInt();

        /**获取imeiLength*/
        int imeiLength = in.readInt();

        /**获取bodyLen*/
        int bodyLen = in.readInt();

        if(in.readableBytes() < bodyLen + imeiLength){
            //数据不够了
            in.resetReaderIndex();
            return;
        }

        byte[] imeiData = new byte[imeiLength];
        in.readBytes(imeiData);// 从 ByteBuf (in 对象) 中读取数据，并将其存入byte[]
        String imei = new String(imeiData);

        byte[] bodyData = new byte[bodyLen];
        in.readBytes(bodyData);

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setAppId(appId);
        messageHeader.setClientType(clientType);
        messageHeader.setCommand(command);
        messageHeader.setLength(bodyLen);
        messageHeader.setVersion(version);
        messageHeader.setMessageType(messageType);
        messageHeader.setImei(imei);

        //数据解析类型
        Message messge = new Message();

        if (messageType == 0X0) {
            String body = new String(bodyData);
            JSONObject parse = (JSONObject) JSONObject.parse(body);
            messge.setMessagePack(parse);//设置数据本身
        }
            messge.setMessageHeader(messageHeader);//设置数据头

        in.markReaderIndex();//更新读索引
        out.add(messge);//message写入管道
    }
}
