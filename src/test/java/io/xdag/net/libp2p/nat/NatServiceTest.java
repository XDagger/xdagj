/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.net.libp2p.nat;

import io.xdag.utils.SafeFuture;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class NatServiceTest {
    private NatManager natManager = mock(NatManager.class);
    private Optional<NatManager> maybeNatManager = Optional.of(natManager);

    @Test
    public void shouldRequestPortsBeMappedOnServiceStart() {
        final NatService natService = new NatService(9000, true, maybeNatManager);
        when(natManager.start()).thenReturn(SafeFuture.completedFuture(null));
        assertThat(natService.start()).isCompleted();
        verify(natManager).start();
        verify(natManager).requestPortForward(eq(9000), eq(NetworkProtocol.UDP), any());
        verify(natManager).requestPortForward(eq(9000), eq(NetworkProtocol.TCP), any());
        verifyNoMoreInteractions(natManager);
    }

    @Test
    public void shouldShutdownNatManager() {
        final NatService natService = new NatService(9000, true, maybeNatManager);
        when(natManager.start()).thenReturn(SafeFuture.completedFuture(null));
        assertThat(natService.start()).isCompleted();

        assertThat(natService.stop()).isCompleted();
        verify(natManager).stop();
    }
}
