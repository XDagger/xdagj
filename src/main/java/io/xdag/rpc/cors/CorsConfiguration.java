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
package io.xdag.rpc.cors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorsConfiguration {
    private static final Logger logger = LoggerFactory.getLogger("cors");
    private final String header;

    public CorsConfiguration(String header) {
        this.header = header;

        if ("*".equals(header)) {
            logger.warn("CORS header set to '*'");
        }

        if (header != null && (header.contains("\n") || header.contains("\r"))) {
            throw new IllegalArgumentException("corsheader");
        }
    }

    public String getHeader() {
        return this.header;
    }

    public boolean hasHeader() {
        return this.header != null && this.header.length() != 0;
    }
}
