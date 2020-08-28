package io.xdag.cli;

import io.xdag.Kernel;
import lombok.extern.slf4j.Slf4j;
import org.jline.builtins.telnet.Telnet;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

@Slf4j
public class TelnetServer {
    private Kernel kernel;
    private String ip;
    private int port;

    public TelnetServer(final String ip, final int port, final Kernel kernel) {
        this.ip = ip;
        this.port = port;
        this.kernel = kernel;
    }

    public void start() {
        try {
            Terminal terminal = TerminalBuilder.builder().build();
            Shell xshell = new Shell();
            xshell.setKernel(kernel);
            Telnet telnetServer = new Telnet(terminal, xshell);
            telnetServer.telnetd(new String[]{"-i" + ip,"-p" + port, "start"});
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
