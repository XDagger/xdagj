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

import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.jni.Native;
import io.xdag.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;

import static io.xdag.crypto.jni.RandomX.*;


public class RandomXTest {

    private Config config;

    @Before
    public void setUp() throws Exception {
        config = new DevnetConfig();
        Native.init(config);
    }

    @Test
    public void rxCacheTest(){
        final String key="hello rx";
        final long rxCache= allocCache();
//        System.out.printf("alloc cache address 0x" + Long.toHexString(rxCache));

        initCache(rxCache,key.getBytes(),key.length());

        releaseCache(rxCache);
//        System.out.printf("release cache address 0x" + Long.toHexString(rxCache));
    }

    @Test
    public void initDataSetTest(){
        final String key="hello rx 1";
        final long rxCache= allocCache();
        initCache(rxCache,key.getBytes(),key.length());
//        System.out.printf("release cache address 0x" + Long.toHexString(rxCache));

        final long rxDataSet=allocDataSet();
        initDataSet(rxCache,rxDataSet,1);

        releaseCache(rxCache);
        releaseDataSet(rxDataSet);
    }

    @Test
    public void initDataSetMutiTest(){
        final String key="hello rx 1";
        final long rxCache= allocCache();
        initCache(rxCache,key.getBytes(),key.length());
//        System.out.printf("release cache address 0x" + Long.toHexString(rxCache));

        final long rxDataSet = allocDataSet();
        initDataSet(rxCache,rxDataSet,4);

        final long rxVm = createVm(rxCache,rxDataSet,4);

        final String data="hello world 1";
        byte[] bs = calculateHash(rxVm,data.getBytes(),data.getBytes().length);
//        System.out.println("get randomx hash " + BytesUtils.toHexString(bs));
        releaseCache(rxCache);
        releaseDataSet(rxDataSet);
        destroyVm(rxVm);
    }

    @Test
    public void allocVmTest(){

    }

    @Test
    public void hashTest(){

    }

    @Test
    public void changeSeedTest(){

    }

}
