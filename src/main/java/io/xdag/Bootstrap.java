/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag;

import io.xdag.config.*;
import io.xdag.wallet.OldWallet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Bootstrap {

    public static Config getConfig(String[] args) throws Exception {
//        if (args == null || args.length == 0) {
//            throw new RuntimeException("getConfig(args) args is empty! ");
//        }
        Config config = new MainnetConfig();
        for (String arg : args) {
            switch (arg) {
                case "-t":
                    config = new TestnetConfig();
                    break;
            }
        }
        config.changePara(args);
        config.setDir();
        //logPoolInfo(oldConfig);

        // init keys
        config.initKeys();
        return config;
    }

    public static void logPoolInfo(Config config) {
        log.info(
                "矿池节点地址 ：[{}:{}], 矿池服务地址：[{}:{}]，相关配置信息：miner[{}],maxip[{}],maxconn[{}],fee[{}],reward[{}],direct[{}],fun[{}]",
                config.getNodeSpec().getNodeIp(),
                config.getNodeSpec().getNodePort(),
                config.getPoolSpec().getPoolIp(),
                config.getPoolSpec().getPoolPort(),
                config.getPoolSpec().getGlobalMinerLimit(),
                config.getPoolSpec().getMaxConnectPerIp(),
                config.getPoolSpec().getMaxMinerPerAccount(),
                config.getPoolSpec().getPoolRation(),
                config.getPoolSpec().getRewardRation(),
                config.getPoolSpec().getDirectRation(),
                config.getPoolSpec().getFundRation());
    }

    public static void main(String[] args) throws Exception {
        Config config = getConfig(args);


        // if dnet_keys.dat and wallet.dat exist
        OldWallet wallet = new OldWallet();

        Kernel kernel = new Kernel(config, wallet);
        // default start kernel
        kernel.testStart();
    }
}
