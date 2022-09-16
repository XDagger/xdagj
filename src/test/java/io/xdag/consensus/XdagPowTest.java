package io.xdag.consensus;

import io.xdag.mine.MinerChannel;
import io.xdag.mine.message.TaskShareMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class XdagPowTest {

    @Mock
    XdagPow xdagPow;

    @Test
    public void testReceiveNewShareOnTimer(){
        //mock 1000 miners send share
        MinerChannel channel = mock(MinerChannel.class);
        TaskShareMessage msg  = mock(TaskShareMessage.class);

//        when(channel.getAddressHash())
        xdagPow.receiveNewShare(channel, msg);

        verify(xdagPow).receiveNewShare(channel, msg);
    }


}
