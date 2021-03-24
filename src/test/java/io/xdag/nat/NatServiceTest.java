package io.xdag.nat;

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
