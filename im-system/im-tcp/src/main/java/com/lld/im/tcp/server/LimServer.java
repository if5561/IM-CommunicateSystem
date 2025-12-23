package com.lld.im.tcp.server;

import com.lld.im.codec.MessageDecoder;
import com.lld.im.codec.MessageEncoder;
import com.lld.im.codec.config.BootstrapConfig;

import com.lld.im.tcp.handler.HeartBeatHandler;
import com.lld.im.tcp.handler.NettyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LimServer {

    private final static Logger logger = LoggerFactory.getLogger(LimServer.class);
    BootstrapConfig.TcpConfig config;
    NioEventLoopGroup mainGroup;
    NioEventLoopGroup subGroup;
    ServerBootstrap server;
    public LimServer(BootstrapConfig.TcpConfig config) {
        this.config=config;
        mainGroup = new NioEventLoopGroup(config.getBossThreadSize());
        subGroup = new NioEventLoopGroup(config.getWorkThreadSize());
        server = new ServerBootstrap();
        server.group(mainGroup,subGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 10240)//服务端可以连接的队列大小
                .option(ChannelOption.SO_REUSEADDR, true)//参数表示允许重复使用本地地址和端口
                .childOption(ChannelOption.TCP_NODELAY, true)//是否禁用Nagle算法 简单来说就是是否批量发送数据，影响消息实时性
                .childOption(ChannelOption.SO_KEEPALIVE, true)//保活开关2h没有数据服务端就会发送心跳包
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new MessageDecoder());
                        ch.pipeline().addLast(new IdleStateHandler
                                (0, 0,1));
                        //心跳检测超时后 会调用下一个handler的userEventTriggered方法
                        ch.pipeline().addLast(new MessageEncoder());
                        ch.pipeline().addLast(new HeartBeatHandler(config.getHeartBeatTime()));
                        ch.pipeline().addLast(new NettyServerHandler(config.getBrokerId(), config.getLogicUrl()));
                    }
                });
    }
    public void start(){
        this.server.bind(this.config.getTcpPort());
    }
}
