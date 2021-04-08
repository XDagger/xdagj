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
package io.xdag.crypto;

import io.xdag.utils.Numeric;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** "Unparameterized" tests of {@link MnemonicUtils}. */
public class StaticMnemonicUtilsTest {
    @Test
    public void testShouldGenerateCorrectEntropyFromMnemonic() {
        // from https://github.com/trezor/python-mnemonic/blob/master/vectors.json
        assertCorrectEntropy(
                "00000000000000000000000000000000",
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon "
                        + "abandon about");
        assertCorrectEntropy(
                "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
                "legal winner thank year wave sausage worth useful legal winner thank yellow");
        assertCorrectEntropy(
                "80808080808080808080808080808080",
                "letter advice cage absurd amount doctor acoustic avoid letter advice cage above");
        assertCorrectEntropy(
                "ffffffffffffffffffffffffffffffff",
                "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong");
        assertCorrectEntropy(
                "9e885d952ad362caeb4efe34a8e91bd2",
                "ozone drill grab fiber curtain grace pudding thank cruise elder eight picnic");
        assertCorrectEntropy(
                "68a79eaca2324873eacc50cb9c6eca8cc68ea5d936f98787c60c7ebc74e6ce7c",
                "hamster diagram private dutch cause delay private meat slide toddler razor book "
                        + "happy fancy gospel tennis maple dilemma loan word shrug inflict delay "
                        + "length",
                64);
        assertCorrectEntropy(
                "23db8160a31d3e0dca3688ed941adbf3",
                "cat swing flag economy stadium alone churn speed unique patch report train");
        assertCorrectEntropy(
                "8080808080808080808080808080808080808080808080808080808080808080",
                "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd"
                        + " amount doctor acoustic avoid letter advice cage absurd amount doctor "
                        + "acoustic bless",
                64);
        assertCorrectEntropy(
                "066dca1a2bb7e8a1db2832148ce9933eea0f3ac9548d793112d9a95c9407efad",
                "all hour make first leader extend hole alien behind guard gospel lava path "
                        + "output census museum junior mass reopen famous sing advance salt reform",
                64);
        assertCorrectEntropy(
                "f30f8c1da665478f49b001d94c5fc452",
                "vessel ladder alter error federal sibling chat ability sun glass valve picture");
        assertCorrectEntropy(
                "c10ec20dc3cd9f652c7fac2f1230f7a3c828389a14392f05",
                "scissors invite lock maple supreme raw rapid void congress muscle digital "
                        + "elegant little brisk hair mango congress clump",
                48);
        assertCorrectEntropy(
                "f585c11aec520db57dd353c69554b21a89b20fb0650966fa0a9d6f74fd989d8f",
                "void come effort suffer camp survey warrior heavy shoot primary clutch crush "
                        + "open amazing screen patrol group space point ten exist slush involve "
                        + "unfold",
                64);
    }

    @Test
    public void testShouldProduceTheSameMnemonic() {
        final String expected =
                "clinic excuse minimum until indoor flower fun concert inquiry letter audit patrol";
        final String actual =
                MnemonicUtils.generateMnemonic(MnemonicUtils.generateEntropy(expected));
        assertEquals(expected, actual);
    }

    @Test
    public void testShouldProduceTheSameEntropy() {
        final byte[] expected = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        final byte[] actual =
                MnemonicUtils.generateEntropy(MnemonicUtils.generateMnemonic(expected));
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testShouldThrowOnEmptyMnemonic() {

        assertThrows(IllegalArgumentException.class, () -> MnemonicUtils.generateEntropy(""));
    }

    private void assertCorrectEntropy(String expected, String mnemonic) {
        assertCorrectEntropy(expected, mnemonic, 32);
    }

    private void assertCorrectEntropy(String expected, String mnemonic, int size) {
        assertEquals(
                expected,
                Numeric.toHexStringNoPrefixZeroPadded(
                        Numeric.toBigInt(MnemonicUtils.generateEntropy(mnemonic)), size));
    }
}

