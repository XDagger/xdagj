package io.xdag.net.discovery.discv5;

import io.xdag.net.discovery.DiscoveryPeer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static io.xdag.net.discovery.discv5.NodeRecordConverter.convertToDiscoveryPeer;
import static org.assertj.core.api.Assertions.assertThat;

public class NodeRecordConverterTest {

    private static final Bytes PUB_KEY =
            Bytes.fromHexString("0x0295A5A50F083697FF8557F3C6FE0CDF8E8EC2141D15F19A5A45571ED9C38CE181");
    private static final Bytes IPV6_LOCALHOST =
            Bytes.fromHexString("0x00000000000000000000000000000001");

    @Test
    public void shouldConvertRealEnrToDiscoveryPeer() throws Exception {
        final String enr =
                "-Iu4QMmfe-EkDnVX6k5i2LFTiDQ-q4-Cb1I01iRI-wbCD_r4Z8eujNCgZDmZXb1ZOPi1LfJaNx3Bd0QUK9wqBjwUXJQBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQO4btn3R6f6mZY_OeOxdrRenoYxCKLRReo6TnbY0JNRlIN0Y3CCIyiDdWRwgiMo";

        final NodeRecord nodeRecord = NodeRecordFactory.DEFAULT.fromBase64(enr);

        final DiscoveryPeer expectedPeer =
                new DiscoveryPeer(
                        Bytes.fromHexString(
                                "0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194"),
                        new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9000));
        assertThat(convertToDiscoveryPeer(nodeRecord)).contains(expectedPeer);
    }

    @Test
    public void shouldNotConvertRecordWithNoIp() {
        assertThat(convertNodeRecordWithFields()).isEmpty();
    }

    @Test
    public void shouldNotConvertRecordWithIpButNoPort() {
        assertThat(
                convertNodeRecordWithFields(
                        new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1}))))
                .isEmpty();
    }

    @Test
    public void shouldNotConvertRecordWithIpAndUdpPortButNoTcpPort() {
        assertThat(
                convertNodeRecordWithFields(
                        new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
                        new EnrField(EnrField.UDP, 30303)))
                .isEmpty();
    }

    @Test
    public void shouldUseV4PortIfV6PortSpecifiedWithNoV6Ip() {
        assertThat(
                convertNodeRecordWithFields(
                        new EnrField(EnrField.IP_V6, IPV6_LOCALHOST), new EnrField(EnrField.TCP, 30303)))
                .contains(new DiscoveryPeer(PUB_KEY, new InetSocketAddress("::1", 30303)));
    }

    @Test
    public void shouldNotConvertRecordWithV4IpAndV6Port() {
        assertThat(
                convertNodeRecordWithFields(
                        new EnrField(EnrField.IP_V4, IPV6_LOCALHOST), new EnrField(EnrField.TCP_V6, 30303)))
                .isEmpty();
    }

    @Test
    public void shouldNotConvertRecordWithPortButNoIp() {
        assertThat(convertNodeRecordWithFields(new EnrField(EnrField.TCP, 30303))).isEmpty();
    }

    @Test
    public void shouldConvertIpV4Record() {
        // IP address bytes are unsigned. Make sure we handle that correctly.
        final Optional<DiscoveryPeer> result =
                convertNodeRecordWithFields(
                        new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {-127, 24, 31, 22})),
                        new EnrField(EnrField.TCP, 1234));
        assertThat(result)
                .contains(new DiscoveryPeer(PUB_KEY, new InetSocketAddress("129.24.31.22", 1234)));
    }

    @Test
    public void shouldConvertIpV6Record() {
        final Optional<DiscoveryPeer> result =
                convertNodeRecordWithFields(
                        new EnrField(EnrField.IP_V6, IPV6_LOCALHOST), new EnrField(EnrField.TCP_V6, 1234));
        assertThat(result).contains(new DiscoveryPeer(PUB_KEY, new InetSocketAddress("::1", 1234)));
    }

    private Optional<DiscoveryPeer> convertNodeRecordWithFields(final EnrField... fields) {
        return convertToDiscoveryPeer(createNodeRecord(fields));
    }

    private NodeRecord createNodeRecord(final EnrField... fields) {
        final ArrayList<EnrField> fieldList = new ArrayList<>(Arrays.asList(fields));
        fieldList.add(new EnrField(EnrField.ID, IdentitySchema.V4));
        fieldList.add(new EnrField(EnrField.PKEY_SECP256K1, PUB_KEY));
        return NodeRecordFactory.DEFAULT.createFromValues(UInt64.ZERO, fieldList);
    }

}