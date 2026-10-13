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

package io.xdag.utils.exception;

import java.io.Serial;

/**
 * Exception thrown when there is an error during codec operations
 */
public class SimpleCodecException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new SimpleCodecException with no message
     */
    public SimpleCodecException() {
    }

    /**
     * Constructs a new SimpleCodecException with the specified message
     * @param s The error message
     */
    public SimpleCodecException(String s) {
        super(s);
    }

    /**
     * Constructs a new SimpleCodecException with the specified message and cause
     * @param s The error message
     * @param throwable The cause of the exception
     */
    public SimpleCodecException(String s, Throwable throwable) {
        super(s, throwable);
    }

    /**
     * Constructs a new SimpleCodecException with the specified cause
     * @param throwable The cause of the exception
     */
    public SimpleCodecException(Throwable throwable) {
        super(throwable);
    }

    /**
     * Constructs a new SimpleCodecException with the specified message, cause, and flags
     * @param s The error message
     * @param throwable The cause of the exception
     * @param enableSuppression Whether suppression is enabled
     * @param writableStackTrace Whether the stack trace should be writable
     */
    public SimpleCodecException(String s, Throwable throwable, boolean enableSuppression, boolean writableStackTrace) {
        super(s, throwable, enableSuppression, writableStackTrace);
    }
}
