package io.xdag.config.spec;

import lombok.Getter;

/**
 * The Admin Specifications
 */
public interface AdminSpec {
    String getTelnetIp();
    int getTelnetPort();
    String getPassword();
}
