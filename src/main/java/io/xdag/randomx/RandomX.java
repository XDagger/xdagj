package io.xdag.randomx;

import io.xdag.core.Block;
import io.xdag.utils.XdagTime;

import static io.xdag.crypto.jni.RandomX.calculateHash;

public class RandomX {



    public void init() {

    }

    public void randomXDifficulty(Block block, long epoch) {
        // rx_pre_hash
        byte[] rx_hash_data1;
        byte[] rx_hash_data0;
        // rx_hash_data0是一次hash的结果

        // rx_hash_data1是block的最后一个字段




        // rx_block_hash
    }

    // 64字节
    public byte[] rx_block_hash(byte[] rx_hash_data,long epoch) {
        long vm=0;
        byte[] hash = calculateHash(vm, rx_hash_data, rx_hash_data.length);
        return hash;
//        g_rx_hash_epoch_index
        //pthread_rwlock_t *rwlock;
        //    rx_pool_mem *rx_memory;
        //
        //    if (g_rx_hash_epoch_index == 0) { // no seed
        //        xdag_info("#!!! rx hash epoch index is 0");
        //        return -1;
        //    } else if (g_rx_hash_epoch_index == 1) { // first seed
        //        rx_memory = &g_rx_pool_mem[g_rx_hash_epoch_index & 1];
        //        if (block_time < rx_memory->switch_time) { // before first seed
        //            xdag_info("#!!! block time %16llx less than switch time %16llx", block_time, rx_memory->switch_time);
        //            return -1;
        //        } else {
        //            rwlock = &g_rx_memory_rwlock[g_rx_hash_epoch_index & 1];
        //        }
        //    } else {
        //        rx_memory = &g_rx_pool_mem[g_rx_hash_epoch_index & 1];
        //        if (block_time < rx_memory->switch_time) {
        //            rwlock = &g_rx_memory_rwlock[(g_rx_hash_epoch_index-1) & 1];
        //            rx_memory = &g_rx_pool_mem[(g_rx_hash_epoch_index-1) & 1];
        //        } else {
        //            rwlock = &g_rx_memory_rwlock[g_rx_hash_epoch_index & 1];
        //        }
        //    }
        //
        //    pthread_rwlock_rdlock(rwlock);
        //    randomx_calculate_hash(rx_memory->block_vm, data, data_size, output_hash);
        //    pthread_rwlock_unlock(rwlock);
        //
        //    return 0;
    }
}
