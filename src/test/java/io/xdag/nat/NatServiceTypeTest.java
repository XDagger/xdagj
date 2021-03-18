package io.xdag.nat;

import junit.framework.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatServiceTypeTest extends TestCase {
    @Test
    void shouldThrowExceptionIfServiceTypeNotValid() {
        assertThatThrownBy(() -> NatServiceType.fromString("invalid"))
                .hasMessageContaining("Invalid NAT service type");
    }

    @ParameterizedTest
    @MethodSource("stringToNatMethod")
    void shouldAcceptValidValues(final String natString, final NatServiceType natServiceType) {
        assertThat(NatServiceType.fromString(natString)).isEqualTo(natServiceType);
    }

    public static Stream<Arguments> stringToNatMethod() {
        Stream.Builder<Arguments> builder = Stream.builder();

        builder.add(Arguments.of("teku_discovery", NatServiceType.XDAG_DISCOVERY));
        builder.add(Arguments.of("teku_p2p", NatServiceType.XDAG_P2P));
        builder.add(Arguments.of("TEKU_DISCOVERY", NatServiceType.XDAG_DISCOVERY));
        builder.add(Arguments.of("TEKU_P2P", NatServiceType.XDAG_P2P));

        return builder.build();
    }
}