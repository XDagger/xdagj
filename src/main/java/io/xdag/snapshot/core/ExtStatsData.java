//package io.xdag.snapshot.core;
//
//import io.xdag.utils.BytesUtils;
//import io.xdag.utils.Numeric;
//import lombok.Data;
//import org.apache.tuweni.bytes.Bytes;
//
//import java.math.BigInteger;
//import java.nio.ByteOrder;
//import java.util.ArrayList;
//import java.util.List;
//
//import static io.xdag.utils.BasicUtils.HASHRATE_LAST_MAX_TIME;
//
//@Data
//public class ExtStatsData {
//
//    List<BigInteger> totalHashrate;
//    List<BigInteger> ourHashrate;
//
//    long hashrateLastTime;
//    long nnoref;
//    long nextra;
//    long nhashes;
//    double hashrateS;
//    int nwaitSync;
//    int cacheSize;
//    int cacheUsage;
//    double cacheHitRate;
//    int useOrphanHashTable;
//
//    public ExtStatsData() {
//    }
//
//    public ExtStatsData(List<BigInteger> totalHashrate, List<BigInteger> ourHashrate, long hashrateLastTime, long nnoref, long nextra, long nhashes, double hashrateS, int nwaitSync, int cacheSize, int cacheUsage, double cacheHitRate, int useOrphanHashTable) {
//        this.totalHashrate = totalHashrate;
//        this.ourHashrate = ourHashrate;
//        this.hashrateLastTime = hashrateLastTime;
//        this.nnoref = nnoref;
//        this.nextra = nextra;
//        this.nhashes = nhashes;
//        this.hashrateS = hashrateS;
//        this.nwaitSync = nwaitSync;
//        this.cacheSize = cacheSize;
//        this.cacheUsage = cacheUsage;
//        this.cacheHitRate = cacheHitRate;
//        this.useOrphanHashTable = useOrphanHashTable;
//    }
//
//    public static ExtStatsData parse(Bytes data) {
//        List<BigInteger> totalHashrate = getHashRate(data.slice(0,4096));
//        List<BigInteger> ourHashrate = getHashRate(data.slice(4096,4096));
//        long hashrateLastTime = data.getLong(8192, ByteOrder.LITTLE_ENDIAN);
//        long nnoref = data.getLong(8200, ByteOrder.LITTLE_ENDIAN);
//        long nextra = data.getLong(8208, ByteOrder.LITTLE_ENDIAN);
//        long nhashes = data.getLong(8216, ByteOrder.LITTLE_ENDIAN);
//        double hashrateS = BytesUtils.hexBytesToDouble(data.slice(8224,16).toArray(),0,true); //8224 x
//
//        int nwaitSync = data.getInt(8240,ByteOrder.LITTLE_ENDIAN); //8224+x 4
//        int cacheSize = data.getInt(8244,ByteOrder.LITTLE_ENDIAN); // 8228+x 4
//        int cacheUsage = data.getInt(8248,ByteOrder.LITTLE_ENDIAN); //8232+x 4
//        double cacheHitRate = BytesUtils.hexBytesToDouble(data.slice(8252,16).toArray(),0,true); //8236+x x
//        int useOrphanHashTable = data.getInt(8268,ByteOrder.LITTLE_ENDIAN); // 8240+2x
//        return new ExtStatsData(totalHashrate,ourHashrate,hashrateLastTime,nnoref,nextra,nhashes,hashrateS,nwaitSync,
//                cacheSize,cacheUsage,cacheHitRate,useOrphanHashTable);
//    }
//
//    private static List<BigInteger> getHashRate(Bytes data) {
//        List<BigInteger> res = new ArrayList<>();
//        int size = data.size()/HASHRATE_LAST_MAX_TIME;
//        for (int i = 0; i < size; i++) {
//            BigInteger diff = data.slice(i*16,16).toBigInteger(ByteOrder.LITTLE_ENDIAN);
//            res.add(diff);
//        }
//        return res;
//    }
//
//    @Override
//    public String toString() {
//        return "ExtStatsData{" +
//                "totalHashrate=" + totalHashrate +
//                ", ourHashrate=" + ourHashrate +
//                ", hashrateLastTime=" + hashrateLastTime +
//                ", nnoref=" + nnoref +
//                ", nextra=" + nextra +
//                ", nhashes=" + nhashes +
//                ", hashrateS=" + hashrateS +
//                ", nwaitSync=" + nwaitSync +
//                ", cacheSize=" + cacheSize +
//                ", cacheUsage=" + cacheUsage +
//                ", cacheHitRate=" + cacheHitRate +
//                ", useOrphanHashTable=" + useOrphanHashTable +
//                '}';
//    }
//}
