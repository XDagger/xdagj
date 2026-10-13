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
import io.xdag.core.AbstractXdagLifecycle;
import lombok.extern.slf4j.Slf4j;
import org.jline.builtins.telnet.Telnet;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Telnet server implementation for remote CLI access
 */
@Slf4j
public class TelnetServer extends AbstractXdagLifecycle {

    private final Kernel kernel;
    private final String ip;
    private final int port;
    private Terminal terminal;
    private Shell xShell;
    private Telnet telnetServer;

    /**
     * Constructor for TelnetServer
     * @param kernel Kernel instance for XDAG operations
     */
    public TelnetServer(final Kernel kernel) {
        this.ip = kernel.getConfig().getAdminSpec().getAdminTelnetIp();
        this.port = kernel.getConfig().getAdminSpec().getAdminTelnetPort();
        this.kernel = kernel;
    }

    /**
     * Starts the telnet server
     * Creates a terminal, initializes the shell and starts listening for connections
     */
    @Override
    protected void doStart() {
        try {
            terminal = TerminalBuilder.builder().build();
            xShell = new Shell();
            xShell.setKernel(kernel);
            telnetServer = new Telnet(terminal, xShell);
            telnetServer.telnetd(new String[]{"-i" + ip, "-p" + port, "start"});
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    protected void doStop() {
        if (telnetServer != null) {
            try {
                terminal.close();
                telnetServer.telnetd(new String[]{"stop"});
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

}
