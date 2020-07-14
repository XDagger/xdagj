package io.xdag;

import io.xdag.cli.Command;
import io.xdag.cli.ShellCommand;
import io.xdag.config.Config;
import io.xdag.wallet.WalletImpl;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

@Slf4j
public class Bootstrap {
    public static void main(String[] args) throws IOException {
        Config config = new Config();
        config.changePara(config, args);
        config.setDir();
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

        try {
            // 初始密钥
            config.initKeys();
        } catch (Exception e) {
            e.printStackTrace();
        }

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

        Terminal terminal = TerminalBuilder.builder().system(true).build();
        LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();
        Kernel kernel = new Kernel(config, wallet);
        ShellCommand shellCommand = new ShellCommand(new Command(kernel));
        String prompt = "xdag> ";
        while (true) {
            String line = null;
            try {
                line = lineReader.readLine(prompt);
                shellCommand.handle(line);
                if (shellCommand.getStopFlag()) {
                    throw new EndOfFileException();
                }
            } catch (UserInterruptException e) {
                // Do nothing
            } catch (EndOfFileException e) {
                System.out.println("\nBye.");
                return;
            }
        }
    }
}
