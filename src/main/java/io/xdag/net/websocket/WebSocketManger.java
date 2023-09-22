package io.xdag.net.websocket;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.xdag.Kernel;
import io.xdag.consensus.PoW;
import io.xdag.consensus.Task;
import lombok.Setter;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.Date;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class WebSocketManger implements Runnable{
    private final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>(100);
    private final Kernel kernel;
    private ScheduledFuture<?> Send_Period_Task;
    private final ExecutorService mainExecutor = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder()
            .namingPattern("WebsocketManager-Main-Thread-%d")
            .daemon(true)
            .build());

    private final ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(1, new BasicThreadFactory.Builder()
            .namingPattern("Websocket_SendTask-Scheduled-Thread-%d")
            .daemon(true)
            .build());
    private volatile Task currentTask;
    @Setter
    private PoW poW;

    private volatile boolean isRunning = false;

    public WebSocketManger(Kernel kernel) {
        this.kernel = kernel;
    }

    @Override
    public void run() {
        while (isRunning) {
            updateNewTaskandBroadcast();
        }
    }

    public void start() throws InterruptedException {
        isRunning = true;
        init();
        mainExecutor.execute(() -> {
            NioEventLoopGroup boss=new NioEventLoopGroup();
            NioEventLoopGroup work=new NioEventLoopGroup();
            try {
                ServerBootstrap bootstrap=new ServerBootstrap();
                bootstrap.group(boss,work);
                bootstrap.channel(NioServerSocketChannel.class);
                bootstrap.childHandler(new WebsocketChannelInitializer(kernel));
                Channel channel = bootstrap.bind(8081).sync().channel();
                log.info("webSocket服务器启动成功："+channel);
                channel.closeFuture().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
                log.info("运行出错："+e);
            }finally {
                boss.shutdownGracefully();
                work.shutdownGracefully();
                log.info("websocket服务器已关闭");
            }
        });

        log.debug("WebsocketManager started.");
    }
    //初始化定时推送线程
    public void init(){
        Send_Period_Task = scheduledExecutor.scheduleAtFixedRate(this::sendPeriodicMessage,0,32, TimeUnit.SECONDS);
    }

    //TODO:这里看一下定时发什么任务
    private void sendPeriodicMessage() {
        // 在这里编写定时发送消息的逻辑
        TextWebSocketFrame tws = new TextWebSocketFrame(new Date()
                + "    这是定时推送信息,推送给："+ kernel.getConfig().getNodeSpec().getNodeTag());
        // 发送消息的代码
        ChannelSupervise.send2All(tws);
    }

    public void stop() {
        isRunning = false;
        close();
        log.debug("WebsocketManager closed.");
    }

    public void close() {
        mainExecutor.shutdown();
        scheduledExecutor.shutdown();
    }


    public void updateNewTaskandBroadcast() {
        Task task = null;
        try {
            task = taskQueue.poll(1,TimeUnit.SECONDS);
        }catch (InterruptedException e){
            log.error(" can not take the task from taskQueue" + e.getMessage(), e);
        }
        if (task != null){
            currentTask = task;
        }

    }

    public void updateTask(Task task) {
        if (!taskQueue.offer(task)) {
            log.debug("Failed to add a task to the queue!");
        }
    }

}

