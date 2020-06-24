package io.xdag.mine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.xdag.Kernel;

/**
 * @Classname MinerServer
 * @Description TODO
 * @Date 2020/5/10 20:01
 * @Created by Myron
 */
public class MinerServer {

    private static final Logger logger = LoggerFactory.getLogger(MinerServer.class);

    protected Kernel kernel;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;


    /** 用来接受监听的fuyire*/
    private ChannelFuture channelFuture;

    /** 是否正在监听 */
    private boolean isListening = false;


    public MinerServer(Kernel kernel) {
        this.kernel = kernel;
    }

    /** 开启监听的事件*/
    public void start() {

        start(kernel.getConfig().getPoolIp(),kernel.getConfig().getPoolPort());

    }

    public void start(String ip, int port) {

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();

            bootstrap.group(bossGroup,workerGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            bootstrap.childOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
            bootstrap.childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, kernel.getConfig().getConnectionTimeout());
            //这个是这是可以远程主动关闭？
            //bootstrap.childOption(ChannelOption.ALLOW_HALF_CLOSURE,true);
            bootstrap.handler(new LoggingHandler());

            bootstrap.childHandler(new MinerChannelInitializer(kernel,true));

            channelFuture = bootstrap.bind(ip,port).sync();
            isListening = true;
            //channelFuture.channel().closeFuture().sync();

            //logger.info("start listening the pool,host:[{}:{}]",ip,port);
            System.out.println("start listening the pool,host:["+ ip +":"+ port +"]");

        }catch (Exception e) {
            logger.error("miner server error: {} ({})", e.getMessage(), e.getClass().getName());
            throw new Error("minerServer Disconnected");
        }

//        }finally {
//            workerGroup.shutdownGracefully();
//            bossGroup.shutdownGracefully();
//        }
    }

    /**关闭连接*/
    public void close() {
        if (isListening && channelFuture != null && channelFuture.channel().isOpen()) {
            try {
                //logger.info("Closing MinerServer...");
                System.out.println("Closing MinerServer...");
                channelFuture.channel().close().sync();

                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();

                isListening = false;

                //logger.info("MinerServer closed.");
                System.out.println("MinerServer closed.");
            } catch (Exception e) {
                logger.warn("Problems closing server channel", e);
            }
        }
    }
}
