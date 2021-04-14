package io.xdag.crypto;

import io.xdag.crypto.jni.Native;
import io.xdag.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;

import static io.xdag.crypto.jni.RandomX.*;


public class RandomXTest {

    @Before
    public void setUp() throws Exception {
        Native.init();
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
