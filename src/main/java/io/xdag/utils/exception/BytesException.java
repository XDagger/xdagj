/**
 * Copyright (c) 2017-2020 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package io.xdag.utils.exception;

public class BytesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BytesException() {
    }

    public BytesException(String s) {
        super(s);
    }

    public BytesException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public BytesException(Throwable throwable) {
        super(throwable);
    }

    public BytesException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
