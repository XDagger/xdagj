package io.xdag.cli;

import io.xdag.Kernel;
import io.xdag.config.Config;
import io.xdag.crypto.jni.Native;
import io.xdag.net.XdagVersion;
import io.xdag.utils.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jline.builtins.Options;
import org.jline.builtins.telnet.Telnet;
import org.jline.console.CommandInput;
import org.jline.console.CommandMethods;
import org.jline.console.CommandRegistry;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.JlineCommandRegistry;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.xdag.utils.BasicUtils.address2Hash;

public class XdagShell implements Telnet.ShellProvider {
    private Kernel kernel;
    private XdagCommands commands;
    private static Path workDir() {
        return Paths.get(System.getProperty("user.dir"));
    }

    public XdagShell(final Kernel kernel) {
        this.kernel = kernel;
        commands = new XdagCommands(kernel);
    }

    protected class XdagJlineCommands extends JlineCommandRegistry implements CommandRegistry {
        private LineReader reader;
        public Map<String, CommandMethods> commandExecute = new HashMap<>();

        public XdagJlineCommands() {
            super();
            commandExecute.put("account", new CommandMethods(this::processAccount, this::defaultCompleter));
            commandExecute.put("balance", new CommandMethods(this::processBalance, this::defaultCompleter));
            commandExecute.put("block", new CommandMethods(this::processBlock, this::defaultCompleter));
            commandExecute.put("lastblocks", new CommandMethods(this::processLastBlocks, this::defaultCompleter));
            commandExecute.put("mainblocks", new CommandMethods(this::processMainBlocks, this::defaultCompleter));
            commandExecute.put("mineblocks", new CommandMethods(this::processMineBlocks, this::defaultCompleter));
            commandExecute.put("state", new CommandMethods(this::processState, this::defaultCompleter));
            commandExecute.put("stats", new CommandMethods(this::processStats, this::defaultCompleter));
            commandExecute.put("xfer", new CommandMethods(this::processXfer, this::defaultCompleter));
            commandExecute.put("miners", new CommandMethods(this::processMiners, this::defaultCompleter));
            commandExecute.put("run", new CommandMethods(this::processRun, this::defaultCompleter));
            commandExecute.put("keygen", new CommandMethods(this::processKeygen, this::defaultCompleter));
            commandExecute.put("net", new CommandMethods(this::processNet, this::defaultCompleter));
            commandExecute.put("disconnect", new CommandMethods(this::processDisconnect, this::defaultCompleter));
            commandExecute.put("terminate", new CommandMethods(this::processTerminate, this::defaultCompleter));
            registerCommands(commandExecute);
        }

        public void setReader(LineReader reader) {
            this.reader = reader;
        }

        private Terminal terminal() {
            return reader.getTerminal();
        }

        private void println(final String msg) {
            terminal().writer().println(msg);
            terminal().writer().flush();
        }

        private boolean checkState() {
            if(commands.getKernelState() == Kernel.State.RUNNING) {
                return true;
            }
            println("Node is not running, please use 'run' command to run the xdag node");
            return false;
        }

        private void processAccount(CommandInput input) {
            final String[] usage = {
                    "account -  print first N (20 by default) our addresses with their amounts",
                    "Usage: account [N]",
                    "  -? --help                       Displays command help"
            };
            try {
                Options opt = parseOptions(usage, input.args());
                List<String> argv = opt.args();
                if(!checkState()) {
                    return;
                }
                int num = 20;
                if (argv.size() > 1) {
                    if (NumberUtils.isDigits(argv.get(1))) {
                        int testnum = NumberUtils.toInt(argv.get(1));
                        if(testnum <= 10000) {
                            num = testnum;
                        }
                    }
                }
                println(commands.account(num));
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processBalance(CommandInput input) {
            final String[] usage = {
                    "balance -  print balance of the address [A] or total balance for all our addresses\n",
                    "Usage: balance [A]",
                    "  -? --help                       Displays command help"
            };
            try {
                Options opt = parseOptions(usage, input.args());
                List<String> argv = opt.args();
                if(!checkState()) {
                    return;
                }
                println(commands.balance(argv.size()>0?argv.get(0):null));
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processBlock(CommandInput input) {
            final String[] usage = {
                    "block -  print extended info for the block corresponding to the address or hash [A]",
                    "Usage: block [A]",
                    "  -? --help                       Displays command help"
            };
            try {
                Options opt = parseOptions(usage, input.args());
                List<String> argv = opt.args();
                if(!checkState()) {
                    return;
                }
                String address = argv.get(0);
                try {
                    byte[] hash = null;
                    if (address.length() == 32) {
                        // as address
                        hash = address2Hash(address);
                    } else {
                        // as hash
                        hash = StringUtils.getHash(address);
                    }
                    if (hash == null) {
                        println("No param");
                        return;
                    }
                    println("Print block info:" + Hex.toHexString(hash));
                    println(commands.block(hash));
                } catch (Exception e) {
                    println("Argument is incorrect.");
                }
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processLastBlocks(CommandInput input) {
            final String[] usage = {
                    "lastblocks - print latest N (20 by default, max limit 100) main blocks",
                    "Usage: lastblocks [N]",
                    "  -? --help                       Displays command help"
            };
            try {
                Options opt = parseOptions(usage, input.args());
                List<String> argv = opt.args();
                if(!checkState()) {
                    return;
                }
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processMainBlocks(CommandInput input) {
            final String[] usage = {
                    "mainblocks -  print latest N (20 by default, max limit 100) main blocks",
                    "Usage: mainblocks [N]",
                    "  -? --help                       Displays command help"
            };
            try {
                Options opt = parseOptions(usage, input.args());
                List<String> argv = opt.args();
                if(!checkState()) {
                    return;
                }
                int num = 20;
                if (argv.size() > 1) {
                    if (NumberUtils.isDigits(argv.get(1))) {
                        int testnum = NumberUtils.toInt(argv.get(1));
                        if(testnum <= 10000) {
                            num = testnum;
                        }
                    }
                }
                println(commands.mainblocks(num));
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processMineBlocks(CommandInput input) {
            final String[] usage = {
                    "mineblocks -  print list of N (20 by default) main blocks mined by current pool",
                    "Usage: mineblocks [N]",
                    "  -? --help                       Displays command help"
            };
            try {
                Options opt = parseOptions(usage, input.args());
                List<String> argv = opt.args();
                if(!checkState()) {
                    return;
                }
                int num = 20;
                if (argv.size() > 1) {
                    if (NumberUtils.isDigits(argv.get(1))) {
                        int testnum = NumberUtils.toInt(argv.get(1));
                        if(testnum <= 10000) {
                            num = testnum;
                        }
                    }
                }
                println(commands.minedblocks(num));
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processState(CommandInput input) {
            final String[] usage = {
                    "state -  print the program state",
                    "Usage: state",
                    "  -? --help                       Displays command help"
            };
            try {
                parseOptions(usage, input.args());
                println(commands.state());

            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processStats(CommandInput input) {
            final String[] usage = {
                    "stats -  print statistics for loaded and all known blocks",
                    "Usage: stats",
                    "  -? --help                       Displays command help"
            };
            try {
                parseOptions(usage, input.args());
                if(!checkState()) {
                    return;
                }
                println(commands.stats());
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processXfer(CommandInput input) {
            final String[] usage = {
                    "xfer -  transfer [S] XDAG to the address [A]",
                    "Usage: transfer [S] XDAG to the address [A]",
                    "  -? --help                       Displays command help"
            };
            try {
                Options opt = parseOptions(usage, input.args());
                List<String> argv = opt.args();
                if(!checkState()) {
                    return;
                }
                byte[] hash;
                double amount = StringUtils.getDouble(argv.get(0));

                if (amount < 0) {
                    println("The transfer amount must be greater than 0");
                    return;
                }

                if (argv.get(1).length() == 32) {
                    hash = address2Hash(argv.get(1));
                } else {
                    hash = StringUtils.getHash(argv.get(1));
                }
                if (hash == null) {
                    println("No param");
                    return;
                }
                if (kernel.getAccountStore().getAccountBlockByHash(hash, false) == null) {
                    println("incorrect address");
                    return;
                }
                // 数据检验都合法 请求用户输入密码
                println("please input your password");
                Scanner scanner = new Scanner(input.terminal().input(), StandardCharsets.UTF_8.name());
                String pwd = scanner.nextLine();
                int err = Native.verify_dnet_key(pwd, kernel.getConfig().getDnetKeyBytes());
                if (err < 0) {
                    //scanner.close();
                    println("The password is incorrect");
                    return;
                }
                //scanner.close();
                println(commands.xfer(amount, hash));

            } catch (Exception e) {
                saveException(e);
                println("Argument is incorrect.");
            }
        }

        private void processMiners(CommandInput input) {
            final String[] usage = {
                    "miners - for pool, print list of recent connected miners",
                    "Usage: miners ",
                    "  -? --help                       Displays command help"
            };
            try {
                parseOptions(usage, input.args());
                if(!checkState()) {
                    return;
                }
                println(commands.miners());
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processRun(CommandInput input) {
            final String[] usage = {
                    "run - run node after loading local blocks if option -r is used",
                    "Usage: run ",
                    "  -? --help                       Displays command help"
            };
            try {
                parseOptions(usage, input.args());
                commands.run();
                println(
                        "Xdag Server system booting up: network = "
                                + (Config.MAINNET ? "MainNet" : "TestNet")
                                + ", version "
                                + XdagVersion.V03 + "(base Xdagger V0.3.1)"
                                + ", user host = ["
                                + commands.getKernel().getConfig().getNodeIp()
                                + ":"
                                + commands.getKernel().getConfig().getNodePort()
                                + "]");
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processKeygen(CommandInput input) {
            final String[] usage = {
                    "keygen - generate new private/public key pair and set it by default",
                    "Usage: keygen ",
                    "  -? --help                       Displays command help"
            };
            try {
                parseOptions(usage, input.args());
                println(commands.keygen());
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processNet(CommandInput input) {
            Pattern p = Pattern.compile("^\\s*(.*?):(\\d+)\\s*$");
            final String[] usage = {
                    "net - run transport layer command, try 'net help'",
                    "Usage: net conn ",
                    "  -? --help                       Displays command help"
            };
            try {
                Options opt = parseOptions(usage, input.args());
                List<String> argv = opt.args();
                if(!checkState()) {
                    return;
                }
                if (argv.size() == 0) {
                    println(
                            "Commands:\n"
                                    + "  conn                          - list connections\n"
                                    + "  connect ip:port               - connect to this host\n"
                                    + "  help                          - print this help");
                    return;
                }
                if ("conn".equals(argv.get(0))) {
                    println(commands.listConnect());
                } else if ("help".equals(argv.get(0))) {
                    println(
                            "Commands:\n"
                                    + "  conn                          - list connections\n"
                                    + "  connect ip:port               - connect to this host\n"
                                    + "  help                          - print this help");
                } else {
                    if ("connect".equals(argv.get(0)) && argv.size()== 2) {
                        println("connect to :" + argv.get(1));
                        Matcher m = p.matcher(argv.get(1));
                        if (m.matches()) {
                            String host = m.group(1);
                            int port = Integer.parseInt(m.group(2));
                            commands.connect(host, port);
                        } else {
                            println("Node ip:port Error");
                        }
                    }
                }
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processDisconnect(CommandInput input) {
            final String[] usage = {
                    "disconnect - disconnect all connections or specified miners",
                    "Usage: disconnect O is option, can be all, address or ip\n" +
                    "                  A is the miners' address\n" +
                    "                 IP is the miners' IP\n",
                    "  -? --help                       Displays command help"
            };
            try {
                Options opt = parseOptions(usage, input.args());
                List<String> argv = opt.args();
                if(!checkState()) {
                    return;
                }
                if(argv.size() > 0) {
                    println(commands.disConnectMinerChannel(argv.get(0)));
                } else {
                    println(usage[1]);
                }
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void processTerminate(CommandInput input) {
            final String[] usage = {
                    "terminate - terminate both daemon and this program",
                    "Usage: terminate ",
                    "  -? --help                       Displays command help"
            };
            try {
                parseOptions(usage, input.args());
                if(!checkState()) {
                    return;
                }
                commands.stop();
                println("stop");
            } catch (Exception e) {
                saveException(e);
            }
        }

    }

    @Override
    public void shell(Terminal terminal, Map<String, String> environment) {
        Parser parser = new DefaultParser();
        XdagJlineCommands commands = new XdagJlineCommands();
        SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, null, null);
        systemRegistry.setCommandRegistries(commands);

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(systemRegistry.completer())
                .parser(parser)
                .variable(LineReader.LIST_MAX, 50)   // max tab completion candidates
                .build();
        commands.setReader(reader);

        final String prompt = "xdag> ";
        while (true) {
            try {
                systemRegistry.cleanUp();
                String line = reader.readLine(prompt);
                systemRegistry.execute(line);
            } catch (Exception e) {
                systemRegistry.trace(e);
            }
        }
    }
}
