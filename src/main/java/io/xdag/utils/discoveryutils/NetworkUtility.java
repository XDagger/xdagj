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
package io.xdag.utils.discoveryutils;

import com.google.common.base.Suppliers;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.function.Supplier;

public class NetworkUtility {

    private NetworkUtility() {}

    private static final Supplier<Boolean> ipv6Available =
            Suppliers.memoize(NetworkUtility::checkIpv6Availability);

    /**
     * The standard for IPv6 availability is if the machine has any IPv6 addresses.
     *
     * @return Returns true if any IPv6 addresses are iterable via {@link NetworkInterface}.
     */
    private static Boolean checkIpv6Availability() {
        try {
            final Enumeration<NetworkInterface> networkInterfaces =
                    NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                final Enumeration<InetAddress> addresses =
                        networkInterfaces.nextElement().getInetAddresses();
                while (addresses.hasMoreElements()) {
                    if (addresses.nextElement() instanceof Inet6Address) {
                        // Found an IPv6 address, hence the IPv6 stack is available.
                        return true;
                    }
                }
            }
        } catch (final Exception ignore) {
            // Any exception means we treat it as not available.
        }
        return false;
    }

    /**
     * Checks the port is not null and is in the valid range port (1-65536).
     *
     * @param port The port to check.
     * @return True if the port is valid, false otherwise.
     */
    public static boolean isValidPort(final int port) {
        return port > 0 && port < 65536;
    }

}

