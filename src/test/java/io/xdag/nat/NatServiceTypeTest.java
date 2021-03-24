package io.xdag.nat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(value = Parameterized.class)
public class NatServiceTypeTest {
    private String natString;
    private NatServiceType natServiceType;

    public NatServiceTypeTest(String natString, NatServiceType natServiceType) {
        this.natString = natString;
        this.natServiceType = natServiceType;
    }

    @Test
    public void shouldAcceptValidValues() {
        assertThat(NatServiceType.fromString(this.natString)).isEqualTo(this.natServiceType);
    }

    @Parameterized.Parameters(name = "Vector set {index} (natString={0}, natServiceType={1})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {
                    "xdag_discovery",
                    NatServiceType.XDAG_DISCOVERY,
                }, {
                    "xdag_p2p",
                    NatServiceType.XDAG_P2P,
                }
        });
    }
}