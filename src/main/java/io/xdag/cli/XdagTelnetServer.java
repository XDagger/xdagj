package io.xdag.cli;

import io.xdag.Kernel;
import org.jline.builtins.telnet.Telnet;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class XdagTelnetServer {
    private Kernel kernel;
    private String ip;
    private int port;

    public XdagTelnetServer(final String ip, final int port, final Kernel kernel) {
        this.ip = ip;
        this.port = port;
        this.kernel = kernel;
    }

    public void start() {
        try {
            Terminal terminal = TerminalBuilder.builder().build();
            Telnet telnetServer = new Telnet(terminal, new XdagShell(kernel));
            telnetServer.telnetd(new String[]{"-i" + ip,"-p" + port, "start"});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
