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

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import io.xdag.cli.Command;
import io.xdag.cli.ShellCommand;
import io.xdag.config.Config;
import io.xdag.wallet.WalletImpl;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Bootstrap {
    
    public static void logPoolInfo(Config config) {
        log.info(
                "矿池节点地址 ：[{}:{}], 矿池服务地址：[{}:{}]，相关配置信息：miner[{}],maxip[{}],maxconn[{}],fee[{}],reward[{}],direct[{}],fun[{}]",
                config.getNodeIp(),
                config.getNodePort(),
                config.getPoolIp(),
                config.getPoolPort(),
                config.getGlobalMinerLimit(),
                config.getMaxConnectPerIp(),
                config.getMaxMinerPerAccount(),
                config.getPoolRation(),
                config.getRewardRation(),
                config.getDirectRation(),
                config.getFundRation());
    }
    
    public static void main(String[] args) throws Exception {
        Config config = new Config();
        config.changePara(config, args);
        config.setDir();
        logPoolInfo(config);

        // 初始密钥
        config.initKeys();

        // 启动前先要判断dnet_keys.dat 与 wallet.dat是否生成
        WalletImpl wallet = new WalletImpl();
        for (int i = 1; i <= 5; i++) {
            try {
                int err = wallet.init(config);
                if (err >= 0) {
                    break;
                } else if (i == 5) {
                    System.out.println("Too many wrong passwords, exit！");
                    System.exit(0);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }
        }

        Terminal terminal = TerminalBuilder.builder().dumb(true).system(true).build();
        LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();
        Kernel kernel = new Kernel(config, wallet);
        ShellCommand shellCommand = new ShellCommand(new Command(kernel));
        String prompt = "xdag> ";
        while (!shellCommand.getStopFlag()) {
            String line = lineReader.readLine(prompt);
            shellCommand.handle(line);

        }
        System.out.println("\nBye.");
    }
}
