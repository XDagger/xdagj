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
import io.xdag.config.Constants;
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
     * Here we make sure that all shutdown hooks will be executed in the order of
     * registration. This is necessary to be manually maintained because
     * ${@link Runtime#addShutdownHook(Thread)} starts shutdown hooks concurrently
     * in unspecified order.
     */
    private static final List<Pair<String, Runnable>> shutdownHooks = Collections.synchronizedList(new ArrayList<>());

    private static final String ENV_XDAGJ_WALLET_PASSWORD = "XDAGJ_WALLET_PASSWORD";

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Launcher::shutdownHook, "shutdown-hook"));
    }

    private final Options options = new Options();
    private String password = null;
    private Config config;

    public Launcher() {
        Option passwordOption = Option.builder()
                .longOpt(XdagOption.PASSWORD.toString())
                .desc("wallet password")
                .hasArg(true).numberOfArgs(1).optionalArg(false).argName("password").type(String.class)
                .build();
        addOption(passwordOption);
    }

    /**
     * Registers a shutdown hook which will be executed in the order of
     * registration.
     */
    public static synchronized void registerShutdownHook(String name, Runnable runnable) {
        shutdownHooks.add(Pair.of(name, runnable));
    }

    /**
     * Call registered shutdown hooks in the order of registration.
     */
    private static synchronized void shutdownHook() {
        // shutdown hooks
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
     * Adds a supported option.
     */
    protected void addOption(Option option) {
        options.addOption(option);
    }

    /**
     * Parses options from the given arguments.
     * Priority: arguments => system property => console input
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

    protected Config buildConfig(String[] args) {
        Config config = null;
        for (String arg : args) {
            if ("-d".equals(arg)) {
                config = new DevnetConfig(Constants.DEFAULT_ROOT_DIR);
                break;
            } else if ("-t".equals(arg)) {
                config = new TestnetConfig(Constants.DEFAULT_ROOT_DIR);
                break;
            } else {
                config = new MainnetConfig(Constants.DEFAULT_ROOT_DIR);
            }
        }
        if (args.length == 0) {
            config = new MainnetConfig(Constants.DEFAULT_ROOT_DIR);
        }

        return config;
    }

}
