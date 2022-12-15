package io.xdag.db;

import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.checkerframework.checker.units.qual.A;

/**
 * @author Dcj_Cory
 * @date 2022/10/31 1:43 PM
 */
@Slf4j
public class AddressStore {
    private static final int AddressSize = 20;
    private final KVSource<byte[], byte[]> AddressSource;
    public static final byte ADDRESS_SIZE = 0x10;
    public static final byte AMOUNT_SUM = 0x20;

    //<addressHash,balance>
    public AddressStore(KVSource<byte[], byte[]> addressSource) {
        AddressSource = addressSource;
    }

    public void init() {
        this.AddressSource.init();
        AddressSource.put(new byte[]{ADDRESS_SIZE}, BytesUtils.longToBytes(0,false));
        AddressSource.put(new byte[]{AMOUNT_SUM},BytesUtils.longToBytes(0,false));
    }

    public void reset() {
        this.AddressSource.reset();
        AddressSource.put(new byte[]{ADDRESS_SIZE}, BytesUtils.longToBytes(0,false));
        AddressSource.put(new byte[]{AMOUNT_SUM},BytesUtils.longToBytes(0,false));
    }

    public UInt64 getBalanceByAddress(byte[] Address){
        byte[] data = AddressSource.get(Address);
        if(data == null){
            log.debug("This pubkey don't exist");
            return UInt64.ZERO;
        }else {
            UInt64 balance = UInt64.fromBytes(Bytes.wrap(data));
            return balance;
        }
    }
//    public UInt64 getBalanceByAddress(String Address){
//        byte[] data;
//        data = AddressSource.get(Hex.decode(Address)) == null ? UInt64.ZERO.toBytes().toArray() : AddressSource.get(Address);
//        UInt64 balance = UInt64.fromBytes(Bytes.wrap(data));
//        return balance;
//    }

    public boolean addressIsExist(byte[] Address){
        return AddressSource.get(Address) != null;
    }

    public void addAddress(byte[] Address){
        AddressSource.put(Address,UInt64.ZERO.toBytes().toArray());
        long currentSize = BytesUtils.bytesToLong(AddressSource.get(new byte[]{ADDRESS_SIZE}),0,false);
        AddressSource.put(new byte[] {ADDRESS_SIZE},BytesUtils.longToBytes(currentSize + 1,false));
    }

    public UInt64 getAllBalance(){
        UInt64 allBalance = UInt64.fromBytes(Bytes.wrap(AddressSource.get(new byte[]{AMOUNT_SUM})));
        return allBalance;
    }

    public void updateAllBalance(UInt64 value){
        AddressSource.put(new byte[]{AMOUNT_SUM},value.toBytes().toArray());
    }
    //TODO：计算上移到应用层
    public void updateBalance(byte[] address,UInt64 value){
        if(address.length != AddressSize){
            log.debug("The Address type is wrong");
            return;
        }
        if(AddressSource.get(address) == null){
            log.debug("This address don't exist");
            addAddress(address);
        }
        AddressSource.put(address,value.toBytes().toArray());
    }
}
