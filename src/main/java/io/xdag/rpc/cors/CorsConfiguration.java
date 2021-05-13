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
