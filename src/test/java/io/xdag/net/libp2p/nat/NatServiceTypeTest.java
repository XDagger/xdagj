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
package io.xdag.net.libp2p.nat;

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