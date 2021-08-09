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

import io.xdag.config.Config;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;

import java.util.*;

@Slf4j
@Getter
@Setter
public class Launcher {

    private final Options options = new Options();
    private String password = null;
    private Config config;

    /**
     * Here we make sure that all shutdown hooks will be executed in the order of
     * registration. This is necessary to be manually maintained because
     * ${@link Runtime#addShutdownHook(Thread)} starts shutdown hooks concurrently
     * in unspecified order.
     */
    private static final List<Pair<String, Runnable>> shutdownHooks = Collections.synchronizedList(new ArrayList<>());

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Launcher::shutdownHook, "shutdown-hook"));
    }

    /**
     * Registers a shutdown hook which will be executed in the order of
     * registration.
     */
    public static synchronized void registerShutdownHook(String name, Runnable runnable) {
        shutdownHooks.add(Pair.of(name, runnable));
    }

    /** Call registered shutdown hooks in the order of registration. */
    private static synchronized void shutdownHook() {
        // shutdown hooks
        for (Pair<String, Runnable> r : shutdownHooks) {
            try {
                log.info("Shutting down {}", r.getLeft());
                r.getRight().run();
            } catch (Exception e) {
                log.debug("Failed to shutdown {}", r.getLeft(), e);
            }
        }
        LogManager.shutdown();
    }

    /**
     * Adds a supported option.
     */
    protected void addOption(Option option) {
        options.addOption(option);
    }

    /**
     * Parses options from the given arguments.
     */
    protected CommandLine parseOptions(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(getOptions(), args);

        return cmd;
    }

    protected Config buildConfig(String[] args) throws Exception {
        Config config = null;
        for (String arg : args) {
            switch (arg) {
                case "-t" -> config = new TestnetConfig();
                default -> config = new MainnetConfig();
            }
        }
        //不传参数默认开启主网配置
        if (args.length == 0) {
            config = new MainnetConfig();
        }
        config.changePara(args);
        config.setDir();
        logPoolInfo(config);

        // init keys
        config.initKeys();

        return config;
    }

    public static void logPoolInfo(Config config) {
        log.info(
                "Xdag Node IP Address：[{}:{}], Xdag Pool Service IP Address：[{}:{}]，Configure：miner[{}],maxip[{}],maxconn[{}],fee[{}],reward[{}],direct[{}],fun[{}]",
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

}
