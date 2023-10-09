package io.xdag.core;

import io.xdag.db.BlockStore;
import org.apache.tuweni.bytes.Bytes32;

import java.util.List;

public class Filter {

    private BlockStore blockStore;

    public Filter(BlockStore blockStore) {
        this.blockStore = blockStore;
    }

    public boolean filterLinkBlock(Block block){
        List<Address> links = block.getLinks();
        for (Address link:links) {
            if(link.getType() != XdagField.FieldType.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     XDAG_FIELD_OUT){
                return true;
            }
        }
        return false;
    }

    public boolean filterTxBlock(Block block){
        List<Address> links = block.getLinks();
        for (Address link:links) {
            if(link.getType() == XdagField.FieldType.XDAG_FIELD_IN || link.getType() == XdagField.FieldType.XDAG_FIELD_INPUT){
                return true;
            }
        }
        return false;
    }

    public boolean filterOurLinkBlock(Bytes32 blockHashLow){
        Block block = blockStore.getBlockByHash(blockHashLow,true);
        if(!filterLinkBlock(block)){
            return block.isOurs();
        }else {
            return true;
        }
    }

}
