package io.xdag.net.libp2p;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import io.xdag.net.libp2p.RPCHandler.Firewall;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class FirewallTest {

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testFirewallNotPropagateTimeoutExceptionUpstream() throws Exception {
        Firewall firewall = new Firewall(Duration.ofMillis(100));
        EmbeddedChannel channel =
                new EmbeddedChannel(
                        firewall,
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                    throws Exception {
                                super.exceptionCaught(ctx, cause);
                            }
                        });
        channel.writeOneOutbound("a");
        executeAllScheduledTasks(channel);
        Assertions.assertThatCode(channel::checkException).doesNotThrowAnyException();
        Assertions.assertThat(channel.isOpen()).isFalse();
    }

    private void executeAllScheduledTasks(EmbeddedChannel channel)
            throws TimeoutException, InterruptedException {
        long waitTime = 0;
        while (waitTime < (long) 5 * 1000) {
            long l = channel.runScheduledPendingTasks();
            if (l < 0) break;
            long ms = l / 1_000_000;
            waitTime += ms;
            Thread.sleep(ms);
        }
        if (waitTime >= (long) 5 * 1000) {
            throw new TimeoutException();
        }
    }
}
