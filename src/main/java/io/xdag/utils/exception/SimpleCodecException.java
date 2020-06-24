/**
 * Copyright (c) 2017-2020 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package io.xdag.utils.exception;

public class SimpleCodecException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SimpleCodecException() {
    }

    public SimpleCodecException(String s) {
        super(s);
    }

    public SimpleCodecException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public SimpleCodecException(Throwable throwable) {
        super(throwable);
    }

    public SimpleCodecException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
