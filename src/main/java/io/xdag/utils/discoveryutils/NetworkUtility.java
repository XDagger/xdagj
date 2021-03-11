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

