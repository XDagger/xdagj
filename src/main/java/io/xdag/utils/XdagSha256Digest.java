package io.xdag.utils;

import java.io.IOException;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.io.DigestOutputStream;
import org.spongycastle.util.Arrays;

public class XdagSha256Digest {
    private SHA256Digest sha256Digest;
    private DigestOutputStream outputStream;

    public XdagSha256Digest() {
        sha256Init();
    }

    public XdagSha256Digest(XdagSha256Digest other) {
        sha256Digest = new SHA256Digest(other.sha256Digest);
        outputStream = new DigestOutputStream(sha256Digest);
    }

    public void sha256Init() {
        sha256Digest = new SHA256Digest();
        outputStream = new DigestOutputStream(sha256Digest);
    }

    public void sha256Update(byte in) throws IOException {
        outputStream.write(in);
    }

    public void sha256Update(byte[] in) throws IOException {
        outputStream.write(in);
    }

    /** double sha256* */
    public byte[] sha256Final(byte[] in) throws IOException {
        outputStream.write(in);
        byte[] hash = outputStream.getDigest();
        sha256Digest.reset();
        outputStream.write(hash);
        byte[] origin = outputStream.getDigest();
        origin = Arrays.reverse(origin);
        return origin;
    }

    /** 获取可以发送给C的state */
    public byte[] getState() {
        byte[] encodedState = sha256Digest.getEncodedState();
        byte[] state = new byte[32];
        System.arraycopy(encodedState, encodedState.length - 32 - 4, state, 0, 32);
        for (int i = 0; i < 32; i += 4) {
            int temp = BytesUtils.bytesToInt(state, i, false);
            System.arraycopy(BytesUtils.intToBytes(temp, true), 0, state, i, 4);
        }
        return state;
    }

    public byte[] getDigest(byte[] in) throws IOException {
        outputStream.write(in);
        byte[] data = outputStream.getDigest();
        return data;
    }

    public byte[] getDigest() {
        byte[] data = outputStream.getDigest();
        return data;
    }

    public byte[] getSha256d(byte[] in) throws IOException {
        return sha256Final(in);
    }
}
