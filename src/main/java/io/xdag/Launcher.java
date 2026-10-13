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

import io.xdag.cli.XdagOption;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.config.MainnetConfig;
import io.xdag.config.TestnetConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;

@Slf4j
@Getter
@Setter
public class Launcher {

    /**
     * Maintains an ordered list of shutdown hooks to ensure proper shutdown sequence.
     * This is necessary since Runtime.addShutdownHook() executes hooks concurrently in random order.
     */
    private static final List<Pair<String, Runnable>> shutdownHooks = Collections.synchronizedList(new ArrayList<>());

    /**
     * Environment variable name for wallet password
     */
    private static final String ENV_XDAGJ_WALLET_PASSWORD = "XDAGJ_WALLET_PASSWORD";

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Launcher::shutdownHook, "shutdown-hook"));
    }

    private final Options options = new Options();
    private String password = null;
    private Config config;

    /**
     * Constructor - initializes command line options
     */
    public Launcher() {
        Option passwordOption = Option.builder()
                .longOpt(XdagOption.PASSWORD.toString())
                .desc("wallet password")
                .hasArg(true).numberOfArgs(1).optionalArg(false).argName("password").type(String.class)
                .build();
        addOption(passwordOption);
    }

    /**
     * Registers a shutdown hook to be executed in registration order
     * @param name Name of the shutdown hook for logging
     * @param runnable The shutdown hook to execute
     */
    public static synchronized void registerShutdownHook(String name, Runnable runnable) {
        shutdownHooks.add(Pair.of(name, runnable));
    }

    /**
     * Executes all registered shutdown hooks in order of registration
     */
    private static synchronized void shutdownHook() {
        for (Pair<String, Runnable> r : shutdownHooks) {
            try {
                log.info("Shutting down {}", r.getLeft());
                r.getRight().run();
            } catch (Exception e) {
                log.error("Failed to shutdown {}", r.getLeft(), e);
            }
        }
        LogManager.shutdown();
    }

    /**
     * Adds a supported command line option
     * @param option The option to add
     */
    protected void addOption(Option option) {
        options.addOption(option);
    }

    /**
     * Parses command line options with priority:
     * 1. Command line arguments
     * 2. System environment variables
     * 3. Console input
     * @param args Command line arguments
     * @return Parsed command line
     */
    protected CommandLine parseOptions(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(getOptions(), args);

        if (cmd.hasOption(XdagOption.PASSWORD.toString())) {
            setPassword(cmd.getOptionValue(XdagOption.PASSWORD.toString()));
        } else if (System.getenv(ENV_XDAGJ_WALLET_PASSWORD) != null) {
            setPassword(System.getenv(ENV_XDAGJ_WALLET_PASSWORD));
        }

        return cmd;
    }

    /**
     * Builds configuration based on command line arguments
     * @param args Command line arguments
     * @return Appropriate network configuration
     */
    protected Config buildConfig(String[] args) {
        Config config = null;
        for (String arg : args) {
            if ("-d".equals(arg)) {
                config = new DevnetConfig();
                break;
            } else if ("-t".equals(arg)) {
                config = new TestnetConfig();
                break;
            } else {
                config = new MainnetConfig();
            }
        }
        if (args.length == 0) {
            config = new MainnetConfig();
        }
        config.changePara(args);
        config.setDir();

        return config;
    }

}
