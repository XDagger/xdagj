package io.xdag.crypto.bip39;

import org.apache.commons.codec.binary.Hex;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.xdag.crypto.bip39.MnemonicCode.WHITESPACE_SPLITTER;

public class MnemonicCodeTest {

    private MnemonicCode mc;

    @Before
    public void setup() throws IOException {
        mc = new MnemonicCode();
    }

    @Test(expected = MnemonicException.MnemonicLengthException.class)
    public void testBadEntropyLength() throws Exception {
        byte[] entropy = Hex.decodeHex("7f7f7f7f7f7f7f7f7f7f7f7f7f7f");
        mc.toMnemonic(entropy);
    }

    @Test(expected = MnemonicException.MnemonicLengthException.class)
    public void testBadLength() throws Exception {
        List<String> words = WHITESPACE_SPLITTER.splitToList("risk tiger venture dinner age assume float denial penalty hello");
        mc.check(words);
    }

    @Test(expected = MnemonicException.MnemonicWordException.class)
    public void testBadWord() throws Exception {
        List<String> words = WHITESPACE_SPLITTER.splitToList("risk tiger venture dinner xyzzy assume float denial penalty hello game wing");
        mc.check(words);
    }

    @Test(expected = MnemonicException.MnemonicChecksumException.class)
    public void testBadChecksum() throws Exception {
        List<String> words = WHITESPACE_SPLITTER.splitToList("bless cloud wheel regular tiny venue bird web grief security dignity zoo");
        mc.check(words);
    }

    @Test(expected = MnemonicException.MnemonicLengthException.class)
    public void testEmptyMnemonic() throws Exception {
        List<String> words = new ArrayList<>();
        mc.check(words);
    }

    @Test(expected = MnemonicException.MnemonicLengthException.class)
    public void testEmptyEntropy() throws Exception {
        byte[] entropy = {};
        mc.toMnemonic(entropy);
    }

    @Test(expected = NullPointerException.class)
    public void testNullPassphrase() throws Exception {
        List<String> code = WHITESPACE_SPLITTER.splitToList("legal winner thank year wave sausage worth useful legal winner thank yellow");
        MnemonicCode.toSeed(code, null);
    }
}
