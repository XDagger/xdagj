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

package io.xdag.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;

import io.xdag.Network;
import io.xdag.core.Fork;

public class MainnetConfig extends AbstractConfig {

    private static final Map<Long, byte[]> checkpoints;
    static {
        HashMap<Long, byte[]> initCheckpoints = new HashMap<>();

        // The apollo fork of xdag mainnet:
//        initCheckpoints.put(1017323L, Hex.decode0x("0xc494425a534d035d9ceb1c05d84ff23b39e9940c5cbd1cbab35fafaffa711e3c"));

        checkpoints = MapUtils.unmodifiableMap(initCheckpoints);
    }

    public MainnetConfig(String dataDir) {
        super(dataDir, Network.MAINNET, Constants.MAINNET_VERSION);
    }

    @Override
    public Map<Long, byte[]> checkpoints() {
        return checkpoints;
    }

    @Override
    public Map<Fork, Long> manuallyActivatedForks() {
        return Collections.emptyMap();
    }

}
