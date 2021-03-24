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
package io.xdag.utils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class IpUtils {
    private static final String IPV6_INPUT_FORMAT = "^\\[(.*)\\]:([0-9]{1,})";
    private static final String IPV4_INPUT_FORMAT = "^([^:]*):([0-9]{1,})";
    private static final Pattern ipv6Pattern = Pattern.compile(IPV6_INPUT_FORMAT);
    private static final Pattern ipv4Pattern = Pattern.compile(IPV4_INPUT_FORMAT);

    public static InetSocketAddress parseAddress(String address) {
        if (StringUtils.isBlank(address)) {
            return null;
        }
        Matcher matcher = ipv6Pattern.matcher(address);
        if (matcher.matches()) {
            return parseMatch(matcher);
        }
        matcher = ipv4Pattern.matcher(address);
        if (matcher.matches() && matcher.groupCount() == 2) {
            return parseMatch(matcher);
        }
        log.debug(
                "Invalid address: {}. For ipv6 use de convention [address]:port. For ipv4 address:port",
                address);
        return null;
    }

    public static List<InetSocketAddress> parseAddresses(List<String> addresses) {
        List<InetSocketAddress> result = new ArrayList<>();
        for (String a : addresses) {
            InetSocketAddress res = parseAddress(a);
            if (res != null) {
                result.add(res);
            }
        }
        return result;
    }

    private static InetSocketAddress parseMatch(Matcher matcher) {
        return new InetSocketAddress(matcher.group(1), Integer.parseInt(matcher.group(2)));
    }

    /**
     * ip地址转换成16进制long
     *
     * @param ipString
     * @return
     */
    public static byte[] ipToLong(String ipString) {
        if (StringUtils.isBlank(ipString)) {
            return null;
        }
        String[] ip = ipString.split("\\.");
        StringBuffer sb = new StringBuffer();
        for (String str : ip) {
            sb.append(Integer.toHexString(Integer.parseInt(str)));
        }
        return Hex.decode(sb.toString());
    }
}
