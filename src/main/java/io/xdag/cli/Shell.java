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

package io.xdag.cli;

import io.xdag.Kernel;
import io.xdag.Wallet;
import io.xdag.utils.BasicUtils;
import io.xdag.utils.WalletUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.jline.builtins.Options;
import org.jline.builtins.TTop;
import org.jline.builtins.telnet.Telnet;
import org.jline.console.CommandInput;
import org.jline.console.CommandMethods;
import org.jline.console.CommandRegistry;
import org.jline.console.impl.JlineCommandRegistry;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.xdag.utils.BasicUtils.address2Hash;
import static io.xdag.utils.BasicUtils.pubAddress2Hash;

@Slf4j
public class Shell extends JlineCommandRegistry implements CommandRegistry, Telnet.ShellProvider {

    public static final int DEFAULT_LIST_NUM = 20;
    public static final String prompt = "xdag> ";
    public Map<String, CommandMethods> commandExecute = new HashMap<>();
    @Setter
    private Kernel kernel;
    private Commands commands;
    private LineReader reader;

    public Shell() {
        super();
        commandExecute.put("account", new CommandMethods(this::processAccount, this::defaultCompleter));
        commandExecute.put("balance", new CommandMethods(this::processBalance, this::defaultCompleter));
        commandExecute.put("block", new CommandMethods(this::processBlock, this::defaultCompleter));
        commandExecute.put("lastblocks", new CommandMethods(this::processLastBlocks, this::defaultCompleter));
        commandExecute.put("mainblocks", new CommandMethods(this::processMainBlocks, this::defaultCompleter));
        commandExecute.put("minedblocks", new CommandMethods(this::processMinedblocks, this::defaultCompleter));
        commandExecute.put("state", new CommandMethods(this::processState, this::defaultCompleter));
        commandExecute.put("stats", new CommandMethods(this::processStats, this::defaultCompleter));
        commandExecute.put("xfer", new CommandMethods(this::processXfer, this::defaultCompleter));
        commandExecute.put("xfertonew", new CommandMethods(this::processXferToNew, this::defaultCompleter));
        commandExecute.put("pool", new CommandMethods(this::processPool, this::defaultCompleter));
        commandExecute.put("keygen", new CommandMethods(this::processKeygen, this::defaultCompleter));
        commandExecute.put("net", new CommandMethods(this::processNet, this::defaultCompleter));
        commandExecute.put("ttop", new CommandMethods(this::processTtop, this::defaultCompleter));
        commandExecute.put("terminate", new CommandMethods(this::processTerminate, this::defaultCompleter));
        commandExecute.put("address", new CommandMethods(this::processAddress, this::defaultCompleter));
        commandExecute.put("oldbalance", new CommandMethods(this::processOldBalance, this::defaultCompleter));
        registerCommands(commandExecute);
    }

    private void processXferToNew(CommandInput input) {
        final String[] usage = {
                "xfertonew -  transfer the old balance to new address \n",
                "Usage: balance xfertonew",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            println(commands.xferToNew());

        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processAddress(CommandInput input) {
        final String[] usage = {
                "address-  print extended info for the account corresponding to the address, page size 100",
                "Usage: address [PUBLIC ADDRESS] [PAGE]",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            List<String> argv = opt.args();
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }

            if (argv.isEmpty()) {
                println("Need hash or address");
                return;
            }

            String address = argv.get(0);
            int page = StringUtils.isNumeric(argv.get(1))?Integer.parseInt(argv.get(1)):1;
            try {
                Bytes32 hash;
                if (WalletUtils.checkAddress(address)) {
                    hash = pubAddress2Hash(address);
                } else {
                    println("Incorrect address");
                    return;
                }
                println(commands.address(hash, page));
            } catch (Exception e) {
                println("Argument is incorrect.");
            }
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processOldBalance(CommandInput input) {
        final String[] usage = {
                "oldbalance -  print max balance we can transfer \n",
                "Usage: balance oldbalance",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            println(commands.balanceMaxXfer());

        } catch (Exception e) {
            saveException(e);
        }
    }

    public void setReader(LineReader reader) {
        this.reader = reader;
    }

    private void println(final String msg) {
        reader.getTerminal().writer().println(msg);
        reader.getTerminal().writer().flush();
    }

    private void processAccount(CommandInput input) {
        final String[] usage = {
                "account -  print first [SIZE] (20 by default) our addresses with their amounts",
                "Usage: account [SIZE]",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            List<String> argv = opt.args();
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            int num = DEFAULT_LIST_NUM;
            if (!argv.isEmpty() && NumberUtils.isDigits(argv.get(0))) {
                num = NumberUtils.toInt(argv.get(0));
            }
            println(commands.account(num));
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processBalance(CommandInput input) {
        final String[] usage = {
                "balance -  print balance of the address [ADDRESS] or total balance for all our addresses\n",
                "Usage: balance [ADDRESS]",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            List<String> argv = opt.args();
            println(commands.balance(!argv.isEmpty() ? argv.get(0) : null));
        } catch (Exception e) {
            saveException(e);
        }
    }


    private void processBlock(CommandInput input) {
        final String[] usage = {
                "block -  print extended info for the block corresponding to the address or hash [A]",
                "Usage: block [ADDRESS|HASH]",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            List<String> argv = opt.args();
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }

            if (argv.isEmpty()) {
                println("Need hash or address");
                return;
            }

            String address = argv.get(0);
            try {
                Bytes32 hash;
                if (address.length() == 32) {
                    // as address
                    hash = address2Hash(address);
                } else {
                    // as hash
                    hash = BasicUtils.getHash(address);
                }
                if (hash == null) {
                    println("No param");
                    return;
                }
                println(commands.block(Bytes32.wrap(hash)));
            } catch (Exception e) {
                println("Argument is incorrect.");
            }
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processLastBlocks(CommandInput input) {
        final String[] usage = {
                "lastblocks - print latest [SIZE] (20 by default, max limit 100) main blocks",
                "Usage: lastblocks [SIZE]",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processMainBlocks(CommandInput input) {
        final String[] usage = {
                "mainblocks -  print latest [SIZE] (20 by default, max limit 100) main blocks",
                "Usage: mainblocks [SIZE]",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            List<String> argv = opt.args();
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            int num = DEFAULT_LIST_NUM;
            if (!argv.isEmpty() && NumberUtils.isDigits(argv.get(0))) {
                num = NumberUtils.toInt(argv.get(0));
            }
            println(commands.mainblocks(num));
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processMinedblocks(CommandInput input) {
        final String[] usage = {
                "mineblocks -  print list of [SIZE] (20 by default) main blocks mined by current pool",
                "Usage: mineblocks [SIZE]",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            List<String> argv = opt.args();
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            int num = DEFAULT_LIST_NUM;
            if (!argv.isEmpty() && NumberUtils.isDigits(argv.get(0))) {
                num = NumberUtils.toInt(argv.get(0));
            }
            println(commands.minedBlocks(num));
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processState(CommandInput input) {
        final String[] usage = {
                "state -  print the program state",
                "Usage: state",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            println(commands.state());

        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processStats(CommandInput input) {
        final String[] usage = {
                "stats -  print statistics for loaded and all known blocks",
                "Usage: stats",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            println(commands.stats());
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processXfer(CommandInput input) {
        final String[] usage = {
                "xfer -  transfer [AMOUNT] XDAG to the address [ADDRESS]",
                "Usage: transfer [AMOUNT] [ADDRESS]",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            List<String> argv = opt.args();
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }

            if (argv.size() < 2) {
                println("Lost some param");
                return;
            }

            Bytes32 hash;
            double amount = BasicUtils.getDouble(argv.get(0));

            String remark = argv.size() == 3 ? argv.get(2) : null;

            if (amount < 0) {
                println("The transfer amount must be greater than 0");
                return;
            }

            if (WalletUtils.checkAddress(argv.get(1))) {
                hash = pubAddress2Hash(argv.get(1));
            } else {
                println("Incorrect address");
                return;
            }

            Wallet wallet = new Wallet(kernel.getConfig());
            if (!wallet.unlock(readPassword())) {
                println("The password is incorrect");
                return;
            }
            println(commands.xfer(amount, hash, remark));

        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processPool(CommandInput input){
        final String[] usage = {
                "pool - for pool, print list of recent connected pool",
                "Usage: pool ",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            println(commands.pool());
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processKeygen(CommandInput input) {
        final String[] usage = {
                "keygen - generate new private/public key pair and set it by default",
                "Usage: keygen",
                "  -? --help                    Show help",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            println(commands.keygen());
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processNet(CommandInput input) {
        Pattern p = Pattern.compile("^\\s*(.*?):(\\d+)\\s*(.*?)$");
        final String[] usage = {
                "net - run transport layer command, try 'net --help'",
                "Usage: net [OPTIONS]",
                "  -? --help                        Show help",
                "  -l --list                 list connections",
                "  -c --connect=IP:PORT     connect to this host",
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            if (opt.isSet("list")) {
                println(commands.listConnect());
                return;
            }
            if (opt.isSet("connect")) {
                println("connect to :" + opt.get("connect"));
                Matcher m = p.matcher(opt.get("connect"));
                if (m.matches()) {
                    String host = m.group(1);
                    int port = Integer.parseInt(m.group(2));
                    commands.connect(host, port);
                } else {
                    println("Node ip:port Error");
                }
            }
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processTtop(CommandInput input) {
        try {
            TTop.ttop(input.terminal(), input.out(), input.err(), input.args());
        } catch (Exception e) {
            saveException(e);
        }
    }

    private void processTerminate(CommandInput input) {
        final String[] usage = {
                "terminate - terminate both daemon and this program",
                "Usage: terminate",
                "  -? --help                       Displays command help"
        };
        try {
            Options opt = parseOptions(usage, input.args());
            if (opt.isSet("help")) {
                throw new Options.HelpException(opt.usage());
            }
            // before terminate must verify admin password(config at AdminSpec)
            if (!readPassword("Enter Admin password> ", true)) {
                return;
            }
            commands.stop();
            println("Stop.");
        } catch (Exception e) {
            saveException(e);
        }
    }

    private boolean readPassword(String prompt, boolean isTelnet) {
        Character mask = '*';
        String line;
        do {
            line = reader.readLine(prompt, mask);
        } while (StringUtils.isEmpty(line));

        if (isTelnet) {
            return line.equals(kernel.getConfig().getAdminSpec().getTelnetPassword());
        }

        return true;
    }

    private String readPassword() {
        Character mask = '*';
        String line;
        do {
            line = reader.readLine(WalletUtils.WALLET_PASSWORD_PROMPT, mask);
        } while (StringUtils.isEmpty(line));
        return line;
    }

    @Override
    public void shell(Terminal terminal, Map<String, String> environment) {
        if (commands == null) {
            commands = new Commands(kernel);
        }
        Parser parser = new DefaultParser();
        SystemRegistryImpl systemRegistry = new SystemRegistryImpl(parser, terminal, null, null);
        systemRegistry.setCommandRegistries(this);
        systemRegistry.setGroupCommandsInHelp(false);

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(systemRegistry.completer())
                .parser(parser)
                .variable(LineReader.LIST_MAX, 50)   // max tab completion candidates
                .build();

        this.setReader(reader);

        if (!readPassword("Enter Admin password>", true)) {
            return;
        }

        do {
            try {
                systemRegistry.cleanUp();
                String line = reader.readLine(prompt);
                if (StringUtils.startsWith(line, "exit")) {
                    break;
                }
                systemRegistry.execute(line);
            } catch (Exception e) {
                systemRegistry.trace(e);
            }
        } while (true);
    }
}
