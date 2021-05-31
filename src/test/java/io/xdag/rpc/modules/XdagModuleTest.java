package io.xdag.rpc.modules;

import io.xdag.rpc.modules.xdag.XdagModule;
import io.xdag.rpc.modules.xdag.XdagModuleTransaction;
import io.xdag.rpc.modules.xdag.XdagModuleWallet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;


public class XdagModuleTest {

    @Test
    public void chainId() {
        XdagModule xdagModule = new XdagModule(
                (byte) 0x21,
                mock(XdagModuleWallet.class),
                mock(XdagModuleTransaction.class)

        );
        assertEquals("0x21", xdagModule.chainId());
    }

    @Test
    public void sendRawTransaction() {

    }
}
