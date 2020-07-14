package io.xdag.basic;

import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import static io.xdag.utils.BasicUtils.*;

public class Address2Hash {

    @Test
    public void TestHash2Address() {
        String news = "42cLWCMWZDKPZM8WJfpmI7Lbe3p83U2l";
        String originhash = "4aa1ab5742feb010a54ddd7c7a7bdbb22366fa2516cf648f32641623580b67e3";
        byte[] hash1 = Hex.decode(originhash);
        assert (hash2Address(hash1).equals(news));
    }

    @Test
    public void TestAddress2Hash() {
        String news = "42cLWCMWZDKPZM8WJfpmI7Lbe3p83U2l";
        String originhashlow = "0000000000000000a54ddd7c7a7bdbb22366fa2516cf648f32641623580b67e3";
        byte[] hashlow = address2Hash(news);
        assert (Hex.toHexString(hashlow).equals(originhashlow));
    }
}
