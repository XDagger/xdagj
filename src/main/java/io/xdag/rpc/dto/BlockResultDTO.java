package io.xdag.rpc.dto;


import io.xdag.core.Block;
import lombok.Data;

@Data
public class BlockResultDTO {

    // blockInfo
    // rawData

    String height;
    String data;
    public BlockResultDTO(long height) {
        this.height = Long.toHexString(height);
        this.data = "hello";
    }



    public static BlockResultDTO fromBlock(Block b, boolean raw) {


        return null;
    }
}
