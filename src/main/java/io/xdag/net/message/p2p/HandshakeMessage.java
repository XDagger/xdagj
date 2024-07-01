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
package io.xdag.net.message.p2p;

import static io.xdag.utils.WalletUtils.toBase58;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;

import io.xdag.Network;
import io.xdag.config.Config;
import io.xdag.utils.SimpleEncoder;
import io.xdag.crypto.Hash;
import io.xdag.crypto.Keys;
import io.xdag.crypto.Sign;
import io.xdag.net.Peer;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageCode;
import io.xdag.utils.SimpleDecoder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class HandshakeMessage extends Message {

    protected final Network network;
    protected final short networkVersion;

    protected final String peerId;
    protected final int port;

    protected final String clientId;
    protected final String[] capabilities;

    protected final long latestBlockNumber;

    protected final byte[] secret;
    protected final long timestamp;
    protected final SECPSignature signature;

    protected SECPPublicKey publicKey;

    public HandshakeMessage(MessageCode code, Class<?> responseMessageClass,
            Network network, short networkVersion, String peerId, int port,
            String clientId, String[] capabilities, long latestBlockNumber,
            byte[] secret, KeyPair coinbase) {
        super(code, responseMessageClass);

        this.network = network;
        this.networkVersion = networkVersion;
        this.peerId = peerId;
        this.port = port;
        this.clientId = clientId;
        this.capabilities = capabilities;
        this.latestBlockNumber = latestBlockNumber;
        this.secret = secret;
        this.timestamp = System.currentTimeMillis();
        this.publicKey = coinbase.getPublicKey();

        SimpleEncoder enc = encodeBasicInfo();
        Bytes32 hash = Hash.sha256(Bytes.wrap(enc.toBytes()));
        this.signature = Sign.SECP256K1.sign(hash, coinbase);

        enc.writeBytes(signature.encodedBytes().toArray());

        this.body = enc.toBytes();
    }

    public HandshakeMessage(MessageCode code, Class<?> responseMessageClass, byte[] body) {
        super(code, responseMessageClass);

        SimpleDecoder dec = new SimpleDecoder(body);
        this.network = Network.of(dec.readByte());
        this.networkVersion = dec.readShort();
        this.peerId = dec.readString();
        this.port = dec.readInt();
        this.clientId = dec.readString();
        List<String> capabilities = new ArrayList<>();
        for (int i = 0, size = dec.readInt(); i < size; i++) {
            capabilities.add(dec.readString());
        }
        this.capabilities = capabilities.toArray(new String[0]);
        this.latestBlockNumber = dec.readLong();
        this.secret = dec.readBytes();
        this.timestamp = dec.readLong();
        this.signature = Sign.SECP256K1.decodeSignature(Bytes.wrap(dec.readBytes()));
        this.body = body;
    }

    protected SimpleEncoder encodeBasicInfo() {
        SimpleEncoder enc = new SimpleEncoder();

        enc.writeByte(network.id());
        enc.writeShort(networkVersion);
        enc.writeString(peerId);
        enc.writeInt(port);
        enc.writeString(clientId);
        enc.writeInt(capabilities.length);
        for (String capability : capabilities) {
            enc.writeString(capability);
        }
        enc.writeLong(latestBlockNumber);
        enc.writeBytes(secret);
        enc.writeLong(timestamp);

        return enc;
    }

    public boolean validate(Config config) {
        SimpleEncoder enc = encodeBasicInfo();
        Bytes32 hash = Hash.sha256(Bytes.wrap(enc.toBytes()));
        if(publicKey == null && signature !=null) {
            publicKey = Sign.SECP256K1.recoverPublicKeyFromSignature(hash, signature).get();
        }
        if (network == config.getNodeSpec().getNetwork()
                && networkVersion == config.getNodeSpec().getNetworkVersion()
                && peerId != null && peerId.length() <= 64
                && port > 0 && port <= 65535
                && clientId != null && clientId.length() < 128
                && latestBlockNumber >= 0
                && secret != null && secret.length == InitMessage.SECRET_LENGTH
                && Math.abs(System.currentTimeMillis() - timestamp) <= config.getNodeSpec().getNetHandshakeExpiry()
                && signature != null
                && peerId.equals(toBase58(Keys.toBytesAddress(publicKey)))) {

            return Sign.SECP256K1.verify(hash, signature, publicKey);
        } else {
            return false;
        }
    }

    /**
     * Constructs a Peer object from the handshake info.
     */
    public Peer getPeer(String ip) {
        return new Peer(network, networkVersion, peerId, ip, port, clientId, capabilities, latestBlockNumber);
    }
}
