package io.xdag.net.message;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.xdag.net.XdagChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageQueue {

	boolean isRunning = false;

	public static final ScheduledExecutorService timer = Executors.newScheduledThreadPool(4, new ThreadFactory() {
		private AtomicInteger cnt = new AtomicInteger(0);

		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, "MessageQueueTimer-" + cnt.getAndIncrement());
		}
	});

	public void receivedMessage(Message msg) throws InterruptedException { // 负责打印记录信息 实际接收信息的业务操作在xdaghandler
		log.debug("MessageQueue接收到新消息");
		if (requestQueue.peek() != null) {
			MessageRoundtrip messageRoundtrip = requestQueue.peek();
			Message waitingMessage = messageRoundtrip.getMsg();

			if (waitingMessage.getAnswerMessage() != null && msg.getClass() == waitingMessage.getAnswerMessage()) {
				messageRoundtrip.answer();
				log.trace("Message round trip covered: [{}] ", messageRoundtrip.getMsg().getClass());
			}
		}
	}

	private Queue<MessageRoundtrip> requestQueue = new ConcurrentLinkedQueue<>();
	private Queue<MessageRoundtrip> respondQueue = new ConcurrentLinkedQueue<>();

	private ChannelHandlerContext ctx = null;

	private ScheduledFuture<?> timerTask;
	private XdagChannel channel;

	public MessageQueue(XdagChannel channel) {
		this.channel = channel;
	}

	public void activate(ChannelHandlerContext ctx) {
		this.ctx = ctx;
		isRunning = true;
		timerTask = timer.scheduleAtFixedRate(() -> {
			try {
				nudgeQueue();
			} catch (Throwable t) {
				log.error("Unhandled exception", t);
			}
		}, 10, 10, TimeUnit.MILLISECONDS); // 10毫米执行一次
	}

	// 每十毫秒执行一次
	private void nudgeQueue() {
		removeAnsweredMessage(requestQueue.peek());
		// Now send the next message
		sendToWire(respondQueue.poll());
		sendToWire(requestQueue.peek());
	}

	public void sendMessage(Message msg) {
		if (channel.isDisconnected()) {
			log.warn("{}: attempt to send [{}] message after disconnect", channel, msg.getCommand().name());
			return;
		}

		if (msg.getAnswerMessage() != null) {

			requestQueue.add(new MessageRoundtrip(msg));
//            log.debug("add new Request message current requestQueue size is:"+requestQueue.size());
		} else {
			respondQueue.add(new MessageRoundtrip(msg));
//            log.debug("add new Response message current responseQueue size is:"+respondQueue.size());

		}
	}

	private void sendToWire(MessageRoundtrip messageRoundtrip) {
		if (messageRoundtrip != null && messageRoundtrip.getRetryTimes() == 0) {
			// TODO: retry logic || messageRoundtrip.hasToRetry()){
			Message msg = messageRoundtrip.getMsg();
//            log.debug("Sent to Wire with the message,msg:"+msg.getCommand());
			ctx.writeAndFlush(msg).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

			if (msg.getAnswerMessage() != null) {
				messageRoundtrip.incRetryTimes();
				messageRoundtrip.saveTime();
			}
		}
	}

	private void removeAnsweredMessage(MessageRoundtrip messageRoundtrip) {
		if (messageRoundtrip != null && messageRoundtrip.isAnswered()) {
			requestQueue.remove();
		}
	}

	public void disconnect() {
		ctx.close();
	}

	public void close() {
		isRunning = false;
		if (timerTask != null) {
			timerTask.cancel(false);
		}
	}

	public boolean isRunning() {
		return isRunning;
	}

	public boolean isIdle() {
		return size() == 0;
	}

	public int size() {
		return requestQueue.size() + respondQueue.size();
	}
}
