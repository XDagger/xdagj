package io.xdag.db;

import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

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

    private void addAddress(byte[] Address){
        AddressSource.put(Address,UInt64.ZERO.toBytes().toArray());
        long currentSize = BytesUtils.bytesToLong(AddressSource.get(new byte[]{AMOUNT_SUM}),0,false);
        AddressSource.put(new byte[] {ADDRESS_SIZE},BytesUtils.longToBytes(currentSize + 1,false));
    }

    public void addBalance(byte[] Address,UInt64 value){
//        if(Address.length != AddressSize){
//            log.debug("The Address type is wrong");
//            return;
//        }
        if(AddressSource.get(Address) == null){
            addAddress(Address);
        }
        UInt64 allAmount = UInt64.fromBytes(Bytes.wrap(AddressSource.get(new byte[]{AMOUNT_SUM})));
        allAmount = allAmount.addExact(value);
        UInt64 balance = UInt64.fromBytes(Bytes.wrap(AddressSource.get(Address)));
        balance = balance.addExact(value);
        AddressSource.put(Address,balance.toBytes().toArray());
        AddressSource.put(new byte[] {AMOUNT_SUM},allAmount.toBytes().toArray());
    }

    public UInt64 getAllBalance(){
        UInt64 allBalance = UInt64.fromBytes(Bytes.wrap(AddressSource.get(new byte[]{AMOUNT_SUM})));
        return allBalance;
    }

    public void subtractBalance(byte[] Address,UInt64 value){
//        if(Address.length != AddressSize){
//            log.debug("The Address type is wrong");
//            return;
//        }
        if(AddressSource.get(Address) == null){
            log.debug("This address don't exist");
            return;
        }
        UInt64 allAmount = UInt64.fromBytes(Bytes.wrap(AddressSource.get(new byte[]{AMOUNT_SUM})));
        allAmount = allAmount.subtractExact(value);
        UInt64 balance = UInt64.fromBytes(Bytes.wrap(AddressSource.get(Address)));
        balance = balance.subtractExact(value);
        AddressSource.put(Address,balance.toBytes().toArray());
        AddressSource.put(new byte[]{AMOUNT_SUM},allAmount.toBytes().toArray());
    }
}
