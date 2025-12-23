package com.lld.im.tcp.server;

import com.lld.im.codec.WebSocketMessageDecoder;
import com.lld.im.codec.WebSocketMessageEncoder;
import com.lld.im.codec.config.BootstrapConfig;
import com.lld.im.tcp.handler.NettyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LimWebSocketServer {

    private final static Logger logger = LoggerFactory.getLogger(LimWebSocketServer.class);
    BootstrapConfig.TcpConfig config;
    NioEventLoopGroup mainGroup;
    NioEventLoopGroup subGroup;
    ServerBootstrap server;

    public LimWebSocketServer(BootstrapConfig.TcpConfig config) {
        this.config = config;
        mainGroup = new NioEventLoopGroup();
        subGroup = new NioEventLoopGroup();
        server = new ServerBootstrap();
        server.group(mainGroup,subGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 10240)//服务端可以连接的队列大小
                .option(ChannelOption.SO_REUSEADDR, true)//参数表示允许重复使用本地地址和端口
                .childOption(ChannelOption.TCP_NODELAY, true)//是否禁用Nagle算法 简单来说就是是否批量发送数据，影响消息实时性
                .childOption(ChannelOption.SO_KEEPALIVE, true)//保活开关2h没有数据服务端就会发送心跳包
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        // websocket 基于http协议，所以要有http编解码器
                        pipeline.addLast("http-codec", new HttpServerCodec());
                        // 对写大数据流的支持
                        pipeline.addLast("http-chunked", new ChunkedWriteHandler());
                        // 几乎在netty中的编程，都会使用到此hanler
                        pipeline.addLast("aggregator", new HttpObjectAggregator(65535));
                        /**
                         * websocket 服务器处理的协议，用于指定给客户端连接访问的路由 : /ws
                         * 本handler会帮你处理一些繁重的复杂的事
                         * 会帮你处理握手动作： handshaking（close, ping, pong） ping + pong = 心跳
                         * 对于websocket来讲，都是以frames进行传输的，不同的数据类型对应的frames也不同
                         */
                        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
                        pipeline.addLast(new WebSocketMessageDecoder());
                        pipeline.addLast(new WebSocketMessageEncoder());
                        pipeline.addLast(new NettyServerHandler(config.getBrokerId(),config.getLogicUrl()));
                    }
                });
    }
    public void start(){
        this.server.bind(this.config.getWebSocketPort());
    }
}
