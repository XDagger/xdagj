package io.xdag.db;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.spongycastle.util.encoders.Hex;

import io.xdag.core.Block;
import io.xdag.utils.BytesUtils;

public class SimpleFileStore implements FileSource {

  private String basicPrefix;

  BufferedInputStream reader;

  public SimpleFileStore(String storePath) {
    basicPrefix = storePath + "/sums/";
  }

  @Override
  public void saveBlockSums(Block block) {
    long size = 512;
    long sum = block.getSum();
    long time = block.getTimestamp();
    List<String> filename = getFileName(time);
    for (int i = 0; i < filename.size(); i++) {
      updateSum(filename.get(i), size, sum, (time >> (40 - 8 * i)) & 0xff);
    }
  }

  public void updateSum(String filename, long size, long sum, long index) {
    StringBuffer filepath = new StringBuffer(basicPrefix);
    if (!"".equals(filename)) {
      filepath.append(filename);
    }

    File file = new File(String.valueOf(filepath));
    if (!file.exists()) {
      file.mkdirs();
    }
    filepath.append("sums.dat");
    file = new File(String.valueOf(filepath));
    RandomAccessFile randomAccessFile = null;
    if (!file.exists()) {
      try {
        file.createNewFile();
        byte[] sums = new byte[4096];
        System.arraycopy(BytesUtils.longToBytes(sum, true), 0, sums, (int) (16 * index), 8);
        System.arraycopy(BytesUtils.longToBytes(size, true), 0, sums, (int) (index * 16 + 8), 8);
        randomAccessFile = new RandomAccessFile(file, "rw");
        randomAccessFile.write(sums);

      } catch (IOException e) {
        e.printStackTrace();
      }
      return;
    }

    byte[] data = new byte[16]; // size + sum
    try {
      randomAccessFile = new RandomAccessFile(file, "rw");
      randomAccessFile.seek(16 * index);
      randomAccessFile.read(data, 0, 16);

      sum += BytesUtils.bytesToLong(data, 0, true);
      size += BytesUtils.bytesToLong(data, 8, true);

      randomAccessFile.seek(16 * index);

      byte[] res = new byte[16];
      System.arraycopy(BytesUtils.longToBytes(sum, true), 0, res, 0, 8);
      System.arraycopy(BytesUtils.longToBytes(size, true), 0, res, 8, 8);
      randomAccessFile.write(res);

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (randomAccessFile != null) {
        try {
          randomAccessFile.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  @Override
  public synchronized byte[] loadSum(long starttime, long endtime) {

    byte[] sums = new byte[256];
    byte[] buf = new byte[4096];
    int level;
    long filedir;

    StringBuffer filename;
    endtime -= starttime;
    if (endtime == 0) { // || (endtime & (endtime-1))!=0
      return null;
    }

    for (level = -6; endtime != 0; level++, endtime >>= 4) {;
    }

    if (level < 2) {
      filedir = (starttime) & 0xffffff000000L;
      long dir1 = (filedir >> 40) & 0xff;
      long dir2 = (filedir >> 32) & 0xff;
      long dir3 = (filedir >> 24) & 0xff;
      filename =
          new StringBuffer(basicPrefix)
              .append(Hex.toHexString(BytesUtils.byteToBytes((byte) dir1, true))).append("/")
              .append(Hex.toHexString(BytesUtils.byteToBytes((byte) dir2, true))).append("/")
              .append(Hex.toHexString(BytesUtils.byteToBytes((byte) dir3, true))).append("/")
              .append("sums.dat");

    } else if (level < 4) {
      filedir = (starttime) & 0xffff00000000L;
      long dir1 = (filedir >> 40) & 0xff;
      long dir2 = (filedir >> 32) & 0xff;
      filename =
          new StringBuffer(basicPrefix)
              .append(Hex.toHexString(BytesUtils.byteToBytes((byte) dir1, true))).append("/")
              .append(Hex.toHexString(BytesUtils.byteToBytes((byte) dir2, true))).append("/")
              .append("sums.dat");

    } else if (level < 6) {
      filedir = (starttime) & 0xff0000000000L;
      long dir1 = (filedir >> 40) & 0xff;
      filename =
          new StringBuffer(basicPrefix)
              .append(Hex.toHexString(BytesUtils.byteToBytes((byte) dir1, true))).append("/")
              .append("sums.dat");
    } else {
      filename = new StringBuffer(basicPrefix).append("sums.dat");
    }

    File file = new File(String.valueOf(filename));
    // 判断文件是否存在
    if (file.exists()) {
      try {
        reader = new BufferedInputStream(new FileInputStream(file));
        reader.read(buf, 0, 4096);
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
    if ((level & 1) != 0) {
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
    } else {
      long index = (starttime >> (level + 4) * 4) & 0xf0;
      System.arraycopy(buf, (int) (index * 16), sums, 0, 16 * 16);
    }

    return sums;
  }

  public List<String> getFileName(long time) {
    List<String> file = new ArrayList<>();
    file.add("");
    StringBuffer stringBuffer =
        new StringBuffer(
            Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 40) & 0xff), true)));
    stringBuffer.append("/");
    file.add(String.valueOf(stringBuffer));
    stringBuffer.append(
        Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 32) & 0xff), true)));
    stringBuffer.append("/");
    file.add(String.valueOf(stringBuffer));
    stringBuffer.append(
        Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 24) & 0xff), true)));
    stringBuffer.append("/");
    file.add(String.valueOf(stringBuffer));
    return file;
  }

  public void reset() {
    File file = new File(basicPrefix);
    delFile(file);
  }

  public void delFile(File file) {
    if (!file.exists()) {
      return;
    }

    if (file.isDirectory()) {
      File[] files = file.listFiles();
      for (File f : files) {
        delFile(f);
      }
    }
    file.delete();
  }
}
