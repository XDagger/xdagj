package io.xdag.db;

import io.xdag.utils.BytesUtils;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.*;

public class SumTest {
    @Test
    public void sumsTest() {
        // String filename = "/Users/punk/Downloads/storage-testnet/01/6f/sums.dat";
        // String filename =
        // "/Users/punk/code/C/xdagOrigin/xdag-master/client/storage-testnet/01/79/sums.dat";//248149504-248132096
        // String filename =
        // "/Users/punk/code/C/xdag/client/storage-testnet4/01/sums.dat";
        // String filenameb =
        // "/Users/punk/code/C/xdag/client/storage-testnet/01/78/ea/d2.dat";
        String filename = "/Users/punk/code/C/xdag/client/storage-testnet/sums.dat";
        String filename2 = "/Users/punk/Documents/XdagJ/Rocksdb/XdagDB/sums/sums.dat";
        // String filename2b = "storage-testnet3/01/78/ea/d2.dat";
        byte[] sums = loadSum(filename);
        byte[] sums2 = loadSum(filename2);
        System.out.println(Hex.toHexString(sums));
        System.out.println(Hex.toHexString(sums2));
        // byte[] block = loadblock(filenameb);
        // byte[] block2 = loadblock(filename2b);
        // System.out.println(Hex.toHexString(block));
        // System.out.println(Hex.toHexString(block2));
    }

    public byte[] loadSum(String filename) {
        BufferedInputStream reader = null;
        File file = new File(filename);
        byte[] sums = new byte[256];
        byte[] buf = new byte[4096];
        if (file.exists()) {
            try {
                reader = new BufferedInputStream(new FileInputStream(file));
                reader.read(buf, 0, 4096);
                System.out.println(filename + " " + Hex.toHexString(buf));
                // System.out.println(Hex.toHexString(buf));

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            return sums;
        }

        long size = 0;
        long sum = 0;
        for (int i = 0; i < 256; i++) {
            long totalsum = BytesUtils.bytesToLong(buf, i * 16, true);
            sum += totalsum;
            long totalsize = BytesUtils.bytesToLong(buf, i * 16 + 8, true);
            size += totalsize;
            if (i % 16 == 0 && i != 0) {
                System.arraycopy(BytesUtils.longToBytes(sum, true), 0, sums, i - 16, 8);
                System.arraycopy(BytesUtils.longToBytes(size, true), 0, sums, i - 8, 8);
                sum = 0;
                size = 0;
            }
        }

        return sums;
    }
}
