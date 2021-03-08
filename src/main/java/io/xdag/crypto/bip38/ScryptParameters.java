package io.xdag.crypto.bip38;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScryptParameters {
    private byte[] salt;
    private long n = 16384;
    private int r = 8;
    private int p = 1;
}