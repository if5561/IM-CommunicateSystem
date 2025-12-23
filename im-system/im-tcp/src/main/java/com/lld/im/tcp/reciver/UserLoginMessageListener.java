package com.lld.im.tcp.reciver;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.proto.MessagePack;
import com.lld.im.common.ClientType;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.enums.DeviceMultiLoginEnum;
import com.lld.im.common.enums.command.SystemCommand;
import com.lld.im.common.model.UserClientDto;
import com.lld.im.tcp.redis.RedisManager;
import com.lld.im.tcp.utils.SessionSocketHolder;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @description:
 * 多端同步：1单端登录：一端在线：踢掉除了本clinetType + imel 的设备
 *          2双端登录：允许pc/mobile 其中一端登录 + web端 踢掉除了本clinetType + imel 以外的web端设备
 *        3 三端登录：允许手机+pc+web，踢掉同端的其他imei 除了web
 *        4 不做任何处理
 *
 * @author: lld
 * @version: 1.0
 */
//用来处理用户下线的一些通知
public class UserLoginMessageListener {

    private final static Logger logger = LoggerFactory.getLogger(UserLoginMessageListener.class);

    private Integer loginModel;

    public UserLoginMessageListener(Integer loginModel) {
        this.loginModel = loginModel;
    }

    public void listenerUserLogin(){
        RTopic topic = RedisManager.getRedissonClient().getTopic(Constants.RedisConstants.UserLoginChannel);
        topic.addListener(String.class, new MessageListener<String>(){//注册消息监听器
            @Override // // 第1个参数：指定要接收的消息类型（这里是 String 类型）第2个参数：匿名内部类（核心是消息处理器）
            public void onMessage(CharSequence charSequence, String msg) {
                //写收到消息后的逻辑 .....
                logger.info("收到用户上线通知 : "+ msg);
                UserClientDto dto = JSONObject.parseObject(msg, UserClientDto.class);
                List<NioSocketChannel> nioSocketChannels = SessionSocketHolder.get(dto.getAppId(), dto.getUserId());
                //↑ 拿到对应用户的所有channels

                for (NioSocketChannel nioSocketChannel : nioSocketChannels) {
                    if(loginModel == DeviceMultiLoginEnum.ONE.getLoginMode()){//单端登录
                        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
                        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.Imei)).get();
                        //组装一个标志
                        if(!(clientType + ":" + imei).equals(dto.getClientType()+":"+dto.getImei())){
                            //todo 踢掉客户端
                            //todo 不能直接踢掉，因为可能还有数据包没传输完毕，可以联动TCP四次挥手，所以这里需要告诉客户端有其他端登录（发送下线通知）
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            //接收端 信息发给谁的 ↑
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);

                        }
                    }else if(loginModel == DeviceMultiLoginEnum.TWO.getLoginMode()){
                        if(dto.getClientType() == ClientType.WEB.getCode()){
                            continue;   //登录端是web 不做处理
                        }
                        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();

                        if (clientType == ClientType.WEB.getCode()){
                            continue;   //判断当前端是不是web端
                                        //通过「新上线的 UserClientDto」和「已在线的 channels」做对比，判断是否踢掉旧连接
                        }
                        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.Imei)).get();
                        if(!(clientType + ":" + imei).equals(dto.getClientType()+":"+dto.getImei())){
                            //todo 踢掉客户端
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            //接收端 信息发给谁的 ↑
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }

                    }else if(loginModel == DeviceMultiLoginEnum.THREE.getLoginMode()){

                        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
                        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.Imei)).get();
                        if(dto.getClientType() == ClientType.WEB.getCode()){
                            continue; //web端不做处理
                        }

                        Boolean isSameClient = false;//检测是否同端 IOS和安卓都是移动 Windows和MAC都是PC端
                        if((clientType == ClientType.IOS.getCode() ||
                                clientType == ClientType.ANDROID.getCode()) &&
                                (dto.getClientType() == ClientType.IOS.getCode() ||
                                        dto.getClientType() == ClientType.ANDROID.getCode())){
                            isSameClient = true; //旧设备信息和新设备信息都是移动端
                        }

                        if((clientType == ClientType.MAC.getCode() ||
                                clientType == ClientType.WINDOWS.getCode()) &&
                                (dto.getClientType() == ClientType.MAC.getCode() ||
                                        dto.getClientType() == ClientType.WINDOWS.getCode())){
                            isSameClient = true;//类似 都是 PC端
                        }

                        if(isSameClient && !(clientType + ":" + imei).equals(dto.getClientType()+":"+dto.getImei())){
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            //接收端 信息发给谁的 ↑
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }
                    }
                }
            }
        });

    }
}
