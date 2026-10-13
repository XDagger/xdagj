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

/**
 * Exception thrown when code execution reaches a point that should be unreachable
 */
public class UnreachableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new UnreachableException with default message
     */
    public UnreachableException() {
        super("Should never reach here");
    }

    /**
     * Constructs a new UnreachableException with specified message, cause and flags
     * @param message The error message
     * @param cause The cause of the exception
     * @param enableSuppression Whether suppression is enabled
     * @param writableStackTrace Whether the stack trace should be writable
     */
    public UnreachableException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Constructs a new UnreachableException with specified message and cause
     * @param message The error message
     * @param cause The cause of the exception
     */
    public UnreachableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new UnreachableException with specified message
     * @param message The error message
     */
    public UnreachableException(String message) {
        super(message);
    }

    /**
     * Constructs a new UnreachableException with specified cause
     * @param cause The cause of the exception
     */
    public UnreachableException(Throwable cause) {
        super(cause);
    }
}
