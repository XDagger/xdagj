package io.xdag.mine.miner;

/**
 * @Classname MinerState
 * @Description 矿池记录矿工的状态
 * @Date 2020/5/7 17:18
 * @Created by Myron
 */
public enum MinerStates {
    /** 未知 表示当前矿工还为注册 */
    MINER_UNKNOWN(0x00),
    /** 活跃 矿工已经注册 且持续工作*/
    MINER_ACTIVE(0x01),
    /** 矿工已经注册，但是没有在挖矿 仅仅是因为还有未支付的余额所以保留档案 */
    MINER_ARCHIVE(0x02),
    /** 服务端 表示这个矿工对象是一个矿池 */
    MINER_SERVICE(0x03);

    private final int cmd;

    MinerStates(int cmd) {
        this.cmd = cmd;
    }

    public static MinerStates fromByte(byte i) throws Exception {
        switch ((int) i) {
        case 0x00:
            return MINER_UNKNOWN;
        case 0x01:
            return MINER_ACTIVE;
        case 0x02:
            return MINER_ARCHIVE;
        case 0x03:
            return MINER_SERVICE;
        default:
            throw new Exception("can find the miner state......please check the param!!");
        }
    }


    public byte asByte() {
        return (byte)(cmd);
    }

    @Override
    public String toString() {
        switch (cmd) {
        case 0x00:
            return "MINER_UNKNOWN";
        case 0x01:
            return "MINER_ACTIVE";
        case 0x02:
            return "MINER_ARCHIVE";
        case 0x03:
            return "MINER_SERVICE";
        default:
            return "can find the miner state......please check the param!!";
        }
    }
}
