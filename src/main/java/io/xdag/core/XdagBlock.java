package io.xdag.core;

import static io.xdag.core.XdagField.FieldType.fromByte;

import org.spongycastle.util.Arrays;

import io.xdag.utils.BytesUtils;

public class XdagBlock {

    public static final int XDAG_BLOCK_FIELDS = 16;
    public static final int XDAG_BLOCK_SIZE = 512;

    /** data 以添加签名 */
    private byte[] data;

    private XdagField[] fields;

    public XdagBlock() {
        fields = new XdagField[XDAG_BLOCK_FIELDS];
    }

    public XdagBlock(XdagField[] fields) {
        this.fields = fields;
    }

    public XdagBlock(byte[] data) {
        this.data = data;
        if (data != null && data.length == 512) {
            fields = new XdagField[XDAG_BLOCK_FIELDS];
            for (int i = 0; i < XDAG_BLOCK_FIELDS; i++) {
                byte[] fieldBytes = new byte[32];
                System.arraycopy(data, i * 32, fieldBytes, 0, 32);
                fields[i] = new XdagField(fieldBytes);
            }
            for (int i = 0; i < XDAG_BLOCK_FIELDS; i++) {
                fields[i].setType(fromByte(getMsgcode(i)));
            }
        }
    }

    /**
     * 获取每个字段的类型
     *
     * @param n
     * @return
     */
    public byte getMsgcode(int n) {
        long type = BytesUtils.bytesToLong(this.data, 8, true);

        return (byte) (type >> (n << 2) & 0xf);
    }

    public XdagField[] getFields() {
        if (this.fields == null) {
            throw new Error("no fields");
        } else {
            return this.fields;
        }
    }

    public XdagField getField(int number) {
        XdagField[] fields = getFields();
        return fields[number];
    }

    public byte[] getData() {
        if (this.data == null) {
            this.data = new byte[512];
            // todo:transfer fields to data
            for (int i = 0; i < XDAG_BLOCK_FIELDS; i++) {
                int index = i * 32;
                System.arraycopy(Arrays.reverse(fields[i].getData()), 0, this.data, index, 32);
            }
        }
        return data;
    }
}
