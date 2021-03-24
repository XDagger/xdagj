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
