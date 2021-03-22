package io.xdag.crypto.bip44;

import com.google.common.io.BaseEncoding;
import io.xdag.crypto.bip32.DeterministicKey;
import io.xdag.crypto.bip44.ChildNumber;
import io.xdag.crypto.bip44.HDKeyDerivation;
import io.xdag.crypto.bip44.HDPath;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

public class HDKeyDerivationTest {

    public static final BaseEncoding HEX = BaseEncoding.base16().lowerCase();

    private static final ChildNumber CHILD_NUMBER = ChildNumber.ONE;
    private static final String EXPECTED_CHILD_CHAIN_CODE = "c4341fe988a2ae6240788c6b21df268b9286769915bed23c7649f263b3643ee8";
    private static final String EXPECTED_CHILD_PRIVATE_KEY = "48516d403070bc93f5e4d78c984cf2d71fc9799293b4eeb3de4f88e3892f523d";
    private static final String EXPECTED_CHILD_PUBLIC_KEY = "036d27f617ce7b0cbdce0abebd1c7aafc147bd406276e6a08d64d7a7ed0ca68f0e";

    @Test
    public void testDeriveFromPrivateParent() {
        DeterministicKey parent = new DeterministicKey(HDPath.M(), new byte[32], BigInteger.TEN,
                null);
        assertFalse(parent.isPubKeyOnly());
        assertFalse(parent.isEncrypted());

        DeterministicKey fromPrivate = HDKeyDerivation.deriveChildKeyFromPrivate(parent, CHILD_NUMBER);
        assertEquals(EXPECTED_CHILD_CHAIN_CODE, HEX.encode(fromPrivate.getChainCode()));
        assertEquals(EXPECTED_CHILD_PRIVATE_KEY, fromPrivate.getPrivateKeyAsHex());
        assertEquals(EXPECTED_CHILD_PUBLIC_KEY, fromPrivate.getPublicKeyAsHex());
        assertFalse(fromPrivate.isPubKeyOnly());
        assertFalse(fromPrivate.isEncrypted());

        DeterministicKey fromPublic = HDKeyDerivation.deriveChildKeyFromPublic(parent, CHILD_NUMBER,
                HDKeyDerivation.PublicDeriveMode.NORMAL);
        assertEquals(EXPECTED_CHILD_CHAIN_CODE, HEX.encode(fromPublic.getChainCode()));
        assertEquals(EXPECTED_CHILD_PRIVATE_KEY, fromPublic.getPrivateKeyAsHex());
        assertEquals(EXPECTED_CHILD_PUBLIC_KEY, fromPublic.getPublicKeyAsHex());
        assertFalse(fromPublic.isPubKeyOnly());
        assertFalse(fromPublic.isEncrypted());

        DeterministicKey fromPublicWithInversion = HDKeyDerivation.deriveChildKeyFromPublic(parent, CHILD_NUMBER,
                HDKeyDerivation.PublicDeriveMode.WITH_INVERSION);
        assertEquals(EXPECTED_CHILD_CHAIN_CODE, HEX.encode(fromPublicWithInversion.getChainCode()));
        assertEquals(EXPECTED_CHILD_PRIVATE_KEY, fromPublicWithInversion.getPrivateKeyAsHex());
        assertEquals(EXPECTED_CHILD_PUBLIC_KEY, fromPublicWithInversion.getPublicKeyAsHex());
        assertFalse(fromPublicWithInversion.isPubKeyOnly());
        assertFalse(fromPublicWithInversion.isEncrypted());
    }

    @Test
    public void testDeriveFromPublicParent() {
        DeterministicKey parent = new DeterministicKey(HDPath.M(), new byte[32], BigInteger.TEN,
                null).dropPrivateBytes();
        assertTrue(parent.isPubKeyOnly());

        try {
            HDKeyDerivation.deriveChildKeyFromPrivate(parent, CHILD_NUMBER);
            fail();
        } catch (IllegalArgumentException x) {
            // expected
        }

        DeterministicKey fromPublic = HDKeyDerivation.deriveChildKeyFromPublic(parent, CHILD_NUMBER,
                HDKeyDerivation.PublicDeriveMode.NORMAL);
        assertEquals(EXPECTED_CHILD_CHAIN_CODE, HEX.encode(fromPublic.getChainCode()));
        assertEquals(EXPECTED_CHILD_PUBLIC_KEY, fromPublic.getPublicKeyAsHex());
        assertTrue(fromPublic.isPubKeyOnly());

        DeterministicKey fromPublicWithInversion = HDKeyDerivation.deriveChildKeyFromPublic(parent, CHILD_NUMBER,
                HDKeyDerivation.PublicDeriveMode.WITH_INVERSION);
        assertEquals(EXPECTED_CHILD_CHAIN_CODE, HEX.encode(fromPublicWithInversion.getChainCode()));
        assertEquals(EXPECTED_CHILD_PUBLIC_KEY, fromPublicWithInversion.getPublicKeyAsHex());
        assertTrue(fromPublicWithInversion.isPubKeyOnly());
    }

}
