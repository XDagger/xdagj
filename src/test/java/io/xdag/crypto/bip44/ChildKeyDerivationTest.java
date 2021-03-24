package io.xdag.crypto.bip44;

import com.google.common.io.BaseEncoding;
import io.xdag.config.Config;
import io.xdag.crypto.ECKey;
import io.xdag.crypto.Sha256Hash;
import io.xdag.crypto.bip32.DeterministicKey;
import io.xdag.crypto.bip38.KeyCrypter;
import io.xdag.crypto.bip38.KeyCrypterScrypt;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChildKeyDerivationTest {
    public static final BaseEncoding HEX = BaseEncoding.base16().lowerCase();

    private static final Config mainnetConfig = new Config();
    private static final int SCRYPT_ITERATIONS = 256;
    private static final int HDW_CHAIN_EXTERNAL = 0;
    private static final int HDW_CHAIN_INTERNAL = 1;

    @Test
    public void testChildKeyDerivation() {
        String[] ckdTestVectors = {
                // test case 1:
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "04" +  "6a04ab98d9e4774ad806e302dddeb63b" +
                        "ea16b5cb5f223ee77478e861bb583eb3" +
                        "36b6fbcb60b5b3d4f1551ac45e5ffc49" +
                        "36466e7d98f6c7c0ec736539f74691a6",
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",

                // test case 2:
                "be05d9ded0a73f81b814c93792f753b35c575fe446760005d44e0be13ba8935a",
                "02" +  "b530da16bbff1428c33020e87fc9e699" +
                        "cc9c753a63b8678ce647b7457397acef",
                "7012bc411228495f25d666d55fdce3f10a93908b5f9b9b7baa6e7573603a7bda"
        };
        assertEquals(0, ckdTestVectors.length % 3);

        for(int i = 0; i < ckdTestVectors.length / 3; i++) {
            byte[] priv  = HEX.decode(ckdTestVectors[3 * i]);
            byte[] pub   = HEX.decode(ckdTestVectors[3 * i + 1]);
            byte[] chain = HEX.decode(ckdTestVectors[3 * i + 2]); // chain code

            //////////////////////////////////////////////////////////////////////////
            // Start with an extended PRIVATE key
            DeterministicKey ekprv = HDKeyDerivation.createMasterPrivKeyFromBytes(priv, chain);
            assertEquals("m", ekprv.getPath().toString());

            // Create two accounts
            DeterministicKey ekprv_0 = HDKeyDerivation.deriveChildKey(ekprv, 0);
            assertEquals("m/0", ekprv_0.getPath().toString());
            DeterministicKey ekprv_1 = HDKeyDerivation.deriveChildKey(ekprv, 1);
            assertEquals("m/1", ekprv_1.getPath().toString());

            // Create internal and external chain on Account 0
            DeterministicKey ekprv_0_EX = HDKeyDerivation.deriveChildKey(ekprv_0, HDW_CHAIN_EXTERNAL);
            assertEquals("m/0/0", ekprv_0_EX.getPath().toString());
            DeterministicKey ekprv_0_IN = HDKeyDerivation.deriveChildKey(ekprv_0, HDW_CHAIN_INTERNAL);
            assertEquals("m/0/1", ekprv_0_IN.getPath().toString());

            // Create three addresses on external chain
            DeterministicKey ekprv_0_EX_0 = HDKeyDerivation.deriveChildKey(ekprv_0_EX, 0);
            assertEquals("m/0/0/0", ekprv_0_EX_0.getPath().toString());
            DeterministicKey ekprv_0_EX_1 = HDKeyDerivation.deriveChildKey(ekprv_0_EX, 1);
            assertEquals("m/0/0/1", ekprv_0_EX_1.getPath().toString());
            DeterministicKey ekprv_0_EX_2 = HDKeyDerivation.deriveChildKey(ekprv_0_EX, 2);
            assertEquals("m/0/0/2", ekprv_0_EX_2.getPath().toString());

            // Create three addresses on internal chain
            DeterministicKey ekprv_0_IN_0 = HDKeyDerivation.deriveChildKey(ekprv_0_IN, 0);
            assertEquals("m/0/1/0", ekprv_0_IN_0.getPath().toString());
            DeterministicKey ekprv_0_IN_1 = HDKeyDerivation.deriveChildKey(ekprv_0_IN, 1);
            assertEquals("m/0/1/1", ekprv_0_IN_1.getPath().toString());
            DeterministicKey ekprv_0_IN_2 = HDKeyDerivation.deriveChildKey(ekprv_0_IN, 2);
            assertEquals("m/0/1/2", ekprv_0_IN_2.getPath().toString());

            // Now add a few more addresses with very large indices
            DeterministicKey ekprv_1_IN = HDKeyDerivation.deriveChildKey(ekprv_1, HDW_CHAIN_INTERNAL);
            assertEquals("m/1/1", ekprv_1_IN.getPath().toString());
            DeterministicKey ekprv_1_IN_4095 = HDKeyDerivation.deriveChildKey(ekprv_1_IN, 4095);
            assertEquals("m/1/1/4095", ekprv_1_IN_4095.getPath().toString());

            //////////////////////////////////////////////////////////////////////////
            // Repeat the above with PUBLIC key
            DeterministicKey ekpub = HDKeyDerivation.createMasterPubKeyFromBytes(HDUtils.toCompressed(pub), chain);
            assertEquals("M", ekpub.getPath().toString());

            // Create two accounts
            DeterministicKey ekpub_0 = HDKeyDerivation.deriveChildKey(ekpub, 0);
            assertEquals("M/0", ekpub_0.getPath().toString());
            DeterministicKey ekpub_1 = HDKeyDerivation.deriveChildKey(ekpub, 1);
            assertEquals("M/1", ekpub_1.getPath().toString());

            // Create internal and external chain on Account 0
            DeterministicKey ekpub_0_EX = HDKeyDerivation.deriveChildKey(ekpub_0, HDW_CHAIN_EXTERNAL);
            assertEquals("M/0/0", ekpub_0_EX.getPath().toString());
            DeterministicKey ekpub_0_IN = HDKeyDerivation.deriveChildKey(ekpub_0, HDW_CHAIN_INTERNAL);
            assertEquals("M/0/1", ekpub_0_IN.getPath().toString());

            // Create three addresses on external chain
            DeterministicKey ekpub_0_EX_0 = HDKeyDerivation.deriveChildKey(ekpub_0_EX, 0);
            assertEquals("M/0/0/0", ekpub_0_EX_0.getPath().toString());
            DeterministicKey ekpub_0_EX_1 = HDKeyDerivation.deriveChildKey(ekpub_0_EX, 1);
            assertEquals("M/0/0/1", ekpub_0_EX_1.getPath().toString());
            DeterministicKey ekpub_0_EX_2 = HDKeyDerivation.deriveChildKey(ekpub_0_EX, 2);
            assertEquals("M/0/0/2", ekpub_0_EX_2.getPath().toString());

            // Create three addresses on internal chain
            DeterministicKey ekpub_0_IN_0 = HDKeyDerivation.deriveChildKey(ekpub_0_IN, 0);
            assertEquals("M/0/1/0", ekpub_0_IN_0.getPath().toString());
            DeterministicKey ekpub_0_IN_1 = HDKeyDerivation.deriveChildKey(ekpub_0_IN, 1);
            assertEquals("M/0/1/1", ekpub_0_IN_1.getPath().toString());
            DeterministicKey ekpub_0_IN_2 = HDKeyDerivation.deriveChildKey(ekpub_0_IN, 2);
            assertEquals("M/0/1/2", ekpub_0_IN_2.getPath().toString());

            // Now add a few more addresses with very large indices
            DeterministicKey ekpub_1_IN = HDKeyDerivation.deriveChildKey(ekpub_1, HDW_CHAIN_INTERNAL);
            assertEquals("M/1/1", ekpub_1_IN.getPath().toString());
            DeterministicKey ekpub_1_IN_4095 = HDKeyDerivation.deriveChildKey(ekpub_1_IN, 4095);
            assertEquals("M/1/1/4095", ekpub_1_IN_4095.getPath().toString());

            assertEquals(hexEncodePub(ekprv.dropPrivateBytes().dropParent()), hexEncodePub(ekpub));
            assertEquals(hexEncodePub(ekprv_0.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_0));
            assertEquals(hexEncodePub(ekprv_1.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_1));
            assertEquals(hexEncodePub(ekprv_0_IN.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_0_IN));
            assertEquals(hexEncodePub(ekprv_0_IN_0.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_0_IN_0));
            assertEquals(hexEncodePub(ekprv_0_IN_1.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_0_IN_1));
            assertEquals(hexEncodePub(ekprv_0_IN_2.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_0_IN_2));
            assertEquals(hexEncodePub(ekprv_0_EX_0.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_0_EX_0));
            assertEquals(hexEncodePub(ekprv_0_EX_1.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_0_EX_1));
            assertEquals(hexEncodePub(ekprv_0_EX_2.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_0_EX_2));
            assertEquals(hexEncodePub(ekprv_1_IN.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_1_IN));
            assertEquals(hexEncodePub(ekprv_1_IN_4095.dropPrivateBytes().dropParent()), hexEncodePub(ekpub_1_IN_4095));
        }
    }

    @Test
    public void inverseEqualsNormal() {
        DeterministicKey key1 = HDKeyDerivation.createMasterPrivateKey("Wired / Aug 13th 2014 / Snowden: I Left the NSA Clues, But They Couldn't Find Them".getBytes());
        HDKeyDerivation.RawKeyBytes key2 = HDKeyDerivation.deriveChildKeyBytesFromPublic(key1.dropPrivateBytes().dropParent(), ChildNumber.ZERO, HDKeyDerivation.PublicDeriveMode.NORMAL);
        HDKeyDerivation.RawKeyBytes key3 = HDKeyDerivation.deriveChildKeyBytesFromPublic(key1.dropPrivateBytes().dropParent(), ChildNumber.ZERO, HDKeyDerivation.PublicDeriveMode.WITH_INVERSION);
        assertArrayEquals(key2.keyBytes, key3.keyBytes);
        assertArrayEquals(key2.chainCode, key3.chainCode);
    }

    @Test
    public void encryptedDerivation() {
        // Check that encrypting a parent key in the hierarchy and then deriving from it yields a DeterministicKey
        // with no private key component, and that the private key bytes are derived on demand.
        KeyCrypter scrypter = new KeyCrypterScrypt(SCRYPT_ITERATIONS);
        KeyParameter aesKey = scrypter.deriveKey("we never went to the moon");

        DeterministicKey key1 = HDKeyDerivation.createMasterPrivateKey("it was all a hoax".getBytes());
        DeterministicKey encryptedKey1 = key1.encrypt(scrypter, aesKey, null);
        DeterministicKey decryptedKey1 = encryptedKey1.decrypt(aesKey);
        assertEquals(key1, decryptedKey1);

        DeterministicKey key2 = HDKeyDerivation.deriveChildKey(key1, ChildNumber.ZERO);
        DeterministicKey derivedKey2 = HDKeyDerivation.deriveChildKey(encryptedKey1, ChildNumber.ZERO);
        assertTrue(derivedKey2.isEncrypted());   // parent is encrypted.
        DeterministicKey decryptedKey2 = derivedKey2.decrypt(aesKey);
        assertFalse(decryptedKey2.isEncrypted());
        assertEquals(key2, decryptedKey2);

        Sha256Hash hash = Sha256Hash.of("the mainstream media won't cover it. why is that?".getBytes());
        try {
            derivedKey2.sign(hash);
            fail();
        } catch (ECKey.KeyIsEncryptedException e) {
            // Ignored.
        }
        ECKey.ECDSASignature signature = derivedKey2.sign(hash, aesKey);
        assertTrue(derivedKey2.verify(hash.getBytes(), signature));
    }

    @Test
    public void pubOnlyDerivation() {
        DeterministicKey key1 = HDKeyDerivation.createMasterPrivateKey("xdag to the moon!".getBytes());
        assertFalse(key1.isPubKeyOnly());
        DeterministicKey key2 = HDKeyDerivation.deriveChildKey(key1, ChildNumber.ZERO_HARDENED);
        assertFalse(key2.isPubKeyOnly());
        DeterministicKey key3 = HDKeyDerivation.deriveChildKey(key2, ChildNumber.ZERO);
        assertFalse(key3.isPubKeyOnly());

        key2 = key2.dropPrivateBytes();
        assertFalse(key2.isPubKeyOnly());   // still got private key bytes from the parents!

        // pubkey2 got its cached private key bytes (if any) dropped, and now it'll lose its parent too, so now it
        // becomes a true pubkey-only object.
        DeterministicKey pubkey2 = key2.dropParent();

        DeterministicKey pubkey3 = HDKeyDerivation.deriveChildKey(pubkey2, ChildNumber.ZERO);
        assertTrue(pubkey3.isPubKeyOnly());
        assertEquals(key3.getPubKeyPoint(), pubkey3.getPubKeyPoint());
    }

    @Test
    public void testSerializationMainConfig() {
        DeterministicKey key1 = HDKeyDerivation.createMasterPrivateKey("xdag to the moon!".getBytes());
        String pub58 = key1.serializePubB58(mainnetConfig);
        String priv58 = key1.serializePrivB58(mainnetConfig);
        assertEquals("xpub661MyMwAqRbcEfMFvsXapkDugHusjgZpyvw2HpW2V89Qm8YiL7Phc2XtDuMhH8J7gsuFhCKdc7B8RMDf2woXpSSXKd5WTV3AqqtD1xWX4nE", pub58);
        assertEquals("xprv9s21ZrQH143K2BGnpqzaTcHB8G5PLDqyci1RVS6QvncRtLDZna5T4EDQNdpBrd7kGUjxHGDYT8hDTYeVzWyCohXiuW7FEdSKpNBjHqtjXuk", priv58);
    }

    @Test
    public void serializeToTextAndBytes() {
        DeterministicKey key1 = HDKeyDerivation.createMasterPrivateKey("xdag to the moon!".getBytes());
        DeterministicKey key2 = HDKeyDerivation.deriveChildKey(key1, ChildNumber.ZERO_HARDENED);

        // Creation time can't survive the xpub serialization format unfortunately.
        key1.setCreationTimeSeconds(0);
        Config params = mainnetConfig;

        {
            final String pub58 = key1.serializePubB58(params);
            final String priv58 = key1.serializePrivB58(params);
            final byte[] pub = key1.serializePublic(params);
            final byte[] priv = key1.serializePrivate(params);
            assertEquals("xpub661MyMwAqRbcEfMFvsXapkDugHusjgZpyvw2HpW2V89Qm8YiL7Phc2XtDuMhH8J7gsuFhCKdc7B8RMDf2woXpSSXKd5WTV3AqqtD1xWX4nE", pub58);
            assertEquals("xprv9s21ZrQH143K2BGnpqzaTcHB8G5PLDqyci1RVS6QvncRtLDZna5T4EDQNdpBrd7kGUjxHGDYT8hDTYeVzWyCohXiuW7FEdSKpNBjHqtjXuk", priv58);
            assertArrayEquals(new byte[]{4,-120,-78,30,0,0,0,0,0,0, 0, 0, 0, 11, -6, 1, 6, 87, 90, 61, -84, 19, -128, -97, 86, -83, -114, -82, -34, -41, 102, 37, -27, 28, 65, 68, -60, 12, 13, 68, -47, -109, -62, 33, 26, 2, -113, -61, -99, 40, 71, -1, -99, -12, -32, 22, -85, -75, -128, 65, -66, 21, 24, -81, -36, -27, -5, 65, -34, -64, 79, 51, 121, 77, 102, 97, 22, 117}, pub);
            assertArrayEquals(new byte[]{4,-120,-83,-28,0,0,0,0,0,0, 0, 0, 0, 11, -6, 1, 6, 87, 90, 61, -84, 19, -128, -97, 86, -83, -114, -82, -34, -41, 102, 37, -27, 28, 65, 68, -60, 12, 13, 68, -47, -109, -62, 33, 26, 0, 76, 59, -62, -17, -117, -72, 18, -46, 41, -1, 17, -60, 83, 123, 29, 26, -84, -44, 13, 92, -112, -36, -69, 23, 75, 84, -87, 57, -88, -39, 47, 82}, priv);
            assertEquals(DeterministicKey.deserializeB58(null, priv58, params), key1);
            assertEquals(DeterministicKey.deserializeB58(priv58, params), key1);
            assertEquals(DeterministicKey.deserializeB58(null, pub58, params).getPubKeyPoint(), key1.getPubKeyPoint());
            assertEquals(DeterministicKey.deserializeB58(pub58, params).getPubKeyPoint(), key1.getPubKeyPoint());
            assertEquals(DeterministicKey.deserialize(params, priv, null), key1);
            assertEquals(DeterministicKey.deserialize(params, priv), key1);
            assertEquals(DeterministicKey.deserialize(params, pub, null).getPubKeyPoint(), key1.getPubKeyPoint());
            assertEquals(DeterministicKey.deserialize(params, pub).getPubKeyPoint(), key1.getPubKeyPoint());
        }
        {
            final String pub58 = key2.serializePubB58(params);
            final String priv58 = key2.serializePrivB58(params);
            final byte[] pub = key2.serializePublic(params);
            final byte[] priv = key2.serializePrivate(params);
            assertEquals(DeterministicKey.deserializeB58(key1, priv58, params), key2);
            assertEquals(DeterministicKey.deserializeB58(key1, pub58, params).getPubKeyPoint(), key2.getPubKeyPoint());
            assertEquals(DeterministicKey.deserialize(params, priv, key1), key2);
            assertEquals(DeterministicKey.deserialize(params, pub, key1).getPubKeyPoint(), key2.getPubKeyPoint());
        }
    }

    @Test
    public void parentlessDeserialization() {
        DeterministicKey key1 = HDKeyDerivation.createMasterPrivateKey("xdag to the moon!".getBytes());
        DeterministicKey key2 = HDKeyDerivation.deriveChildKey(key1, ChildNumber.ZERO_HARDENED);
        DeterministicKey key3 = HDKeyDerivation.deriveChildKey(key2, ChildNumber.ZERO_HARDENED);
        DeterministicKey key4 = HDKeyDerivation.deriveChildKey(key3, ChildNumber.ZERO_HARDENED);
        assertEquals(key4.getPath().size(), 3);
        assertEquals(DeterministicKey.deserialize(mainnetConfig, key4.serializePrivate(mainnetConfig), key3).getPath().size(), 3);
        assertEquals(DeterministicKey.deserialize(mainnetConfig, key4.serializePrivate(mainnetConfig), null).getPath().size(), 1);
        assertEquals(DeterministicKey.deserialize(mainnetConfig, key4.serializePrivate(mainnetConfig)).getPath().size(), 1);
    }

    /** Reserializing a deserialized key should yield the original input */
    @Test
    public void reserialization() {
        // This is the public encoding of the key with path m/0H/1/2H from BIP32 published test vector 1:
        // https://en.bitcoin.it/wiki/BIP_0032_TestVectors
        String encoded =
                "xpub6D4BDPcP2GT577Vvch3R8wDkScZWzQzMMUm3PWbmWvVJrZwQY4VUNgqFJPMM3No2dFDFGTsxxpG5uJh7n7epu4trkrX7x7DogT5Uv6fcLW5";
        DeterministicKey key = DeterministicKey.deserializeB58(encoded, mainnetConfig);
        assertEquals("Reserialized parentless private HD key is wrong", key.serializePubB58(mainnetConfig), encoded);
        assertEquals("Depth of deserialized parentless public HD key is wrong", key.getDepth(), 3);
        assertEquals("Path size of deserialized parentless public HD key is wrong", key.getPath().size(), 1);
        assertEquals("Parent fingerprint of deserialized parentless public HD key is wrong",
                key.getParentFingerprint(), 0xbef5a2f9);

        // This encoding is the same key but including its private data:
        encoded =
                "xprv9z4pot5VBttmtdRTWfWQmoH1taj2axGVzFqSb8C9xaxKymcFzXBDptWmT7FwuEzG3ryjH4ktypQSAewRiNMjANTtpgP4mLTj34bhnZX7UiM";
        key = DeterministicKey.deserializeB58(encoded, mainnetConfig);
        assertEquals("Reserialized parentless private HD key is wrong", key.serializePrivB58(mainnetConfig), encoded);
        assertEquals("Depth of deserialized parentless private HD key is wrong", key.getDepth(), 3);
        assertEquals("Path size of deserialized parentless private HD key is wrong", key.getPath().size(), 1);
        assertEquals("Parent fingerprint of deserialized parentless private HD key is wrong",
                key.getParentFingerprint(), 0xbef5a2f9);

        // These encodings are of the the root key of that hierarchy
        assertEquals("Parent fingerprint of root node public HD key should be zero",
                DeterministicKey.deserializeB58("xpub661MyMwAqRbcFW31YEwpkMuc5THy2PSt5bDMsktWQcFF8syAmRUapSCGu8ED9W6oDMSgv6Zz8idoc4a6mr8BDzTJY47LJhkJ8UB7WEGuduB", mainnetConfig).getParentFingerprint(),
                0);
        assertEquals("Parent fingerprint of root node private HD key should be zero",
                DeterministicKey.deserializeB58("xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8NSpSBHNqPqm55Qn3LqFtT2emdEXVYsCzC2U", mainnetConfig).getParentFingerprint(),
                0);

    }

    private static String hexEncodePub(DeterministicKey pubKey) {
        return HEX.encode(pubKey.getPubKey());
    }
}
