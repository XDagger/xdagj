//package io.xdag.libp2p.Utils;
//
//import lombok.extern.slf4j.Slf4j;
//
//import java.io.IOException;
//import java.net.Inet6Address;
//import java.net.InetAddress;
//import java.net.NetworkInterface;
//import java.net.UnknownHostException;
//import java.util.Enumeration;
//import java.util.regex.Pattern;
//
//@Slf4j
//public class IpUtil {
//    private static final String ANY_HOST_VALUE = "0.0.0.0";
//    private static final String LOCALHOST_VALUE = "127.0.0.1";
//    private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3,5}$");
//
//
//    private static volatile InetAddress LOCAL_ADDRESS = null;
//
//    private static InetAddress toValidAddress(InetAddress address) {
//        if (address instanceof Inet6Address) {
//            Inet6Address v6Address = (Inet6Address) address;
//            if (isPreferIPV6Address()) {
//                return normalizeV6Address(v6Address);
//            }
//        }
//        if (isValidV4Address(address)) {
//            return address;
//        }
//        return null;
//    }
//
//    private static boolean isPreferIPV6Address() {
//        return Boolean.getBoolean("java.net.preferIPv6Addresses");
//    }
//
//    private static boolean isValidV4Address(InetAddress address) {
//        if (address == null || address.isLoopbackAddress()) {
//            return false;
//        }
//        String name = address.getHostAddress();
//        boolean result = (name != null
//                && IP_PATTERN.matcher(name).matches()
//                && !ANY_HOST_VALUE.equals(name)
//                && !LOCALHOST_VALUE.equals(name));
//        return result;
//    }
//
//
//    private static InetAddress normalizeV6Address(Inet6Address address) {
//        String addr = address.getHostAddress();
//        int i = addr.lastIndexOf('%');
//        if (i > 0) {
//            try {
//                return InetAddress.getByName(addr.substring(0, i) + '%' + address.getScopeId());
//            } catch (UnknownHostException e) {
//                // ignore
//                log.debug("Unknown IPV6 address: ", e);
//            }
//        }
//        return address;
//    }
//
//
//    private static InetAddress getLocalAddress0() {
//        InetAddress localAddress = null;
//        try {
//            localAddress = InetAddress.getLocalHost();
//            InetAddress addressItem = toValidAddress(localAddress);
//            if (addressItem != null) {
//                return addressItem;
//            }
//        } catch (Throwable e) {
//            log.error(e.getMessage(), e);
//        }
//
//        try {
//            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
//            if (null == interfaces) {
//                return localAddress;
//            }
//            while (interfaces.hasMoreElements()) {
//                try {
//                    NetworkInterface network = interfaces.nextElement();
//                    if (network.isLoopback() || network.isVirtual() || !network.isUp()) {
//                        continue;
//                    }
//                    Enumeration<InetAddress> addresses = network.getInetAddresses();
//                    while (addresses.hasMoreElements()) {
//                        try {
//                            InetAddress addressItem = toValidAddress(addresses.nextElement());
//                            if (addressItem != null) {
//                                try {
//                                    if (addressItem.isReachable(100)) {
//                                        return addressItem;
//                                    }
//                                } catch (IOException e) {
//                                    // ignore
//                                }
//                            }
//                        } catch (Throwable e) {
//                            log.error(e.getMessage(), e);
//                        }
//                    }
//                } catch (Throwable e) {
//                    log.error(e.getMessage(), e);
//                }
//            }
//        } catch (Throwable e) {
//            log.error(e.getMessage(), e);
//        }
//        return localAddress;
//    }
//
//
//    public static InetAddress getLocalAddress() {
//        if (LOCAL_ADDRESS != null) {
//            return LOCAL_ADDRESS;
//        }
//        InetAddress localAddress = getLocalAddress0();
//        LOCAL_ADDRESS = localAddress;
//        return localAddress;
//    }
//
//    public static String getIp() {
//        return getLocalAddress().getHostAddress();
//    }
//
//    public static String getIpPort(int port) {
//        String ip = getIp();
//        return getIpPort(ip, port);
//    }
//
//    public static String getIpPort(String ip, int port) {
//        if (ip == null) {
//            return null;
//        }
//        return ip.concat(":").concat(String.valueOf(port));
//    }
//
//    public static Object[] parseIpPort(String address) {
//        String[] array = address.split(":");
//
//        String host = array[0];
//        int port = Integer.parseInt(array[1]);
//
//        return new Object[]{host, port};
//    }
//}
//
//
