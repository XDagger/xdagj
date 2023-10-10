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

import java.util.function.Supplier;

public class Preconditions {
    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     * @param expression a boolean expression
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression) {
        check(expression, IllegalArgumentException::new);
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     * @param expression a boolean expression
     * @param messageSupplier supplier of the detail message to be used in the event that a IllegalArgumentException is thrown
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression, Supplier<String> messageSupplier) {
        check(expression, () -> new IllegalArgumentException(messageSupplier.get()));
    }

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     * @param expression a boolean expression
     * @throws IllegalStateException if {@code expression} is false
     */
    public static void checkState(boolean expression) {
        check(expression, IllegalStateException::new);
    }

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     * @param expression a boolean expression
     * @param messageSupplier supplier of the detail message to be used in the event that a IllegalStateException is thrown
     * @throws IllegalStateException if {@code expression} is false
     */
    public static void checkState(boolean expression, Supplier<String> messageSupplier) {
        check(expression, () -> new IllegalStateException(messageSupplier.get()));
    }

    /**
     * Ensures the truth of an expression, throwing a custom exception if untrue.
     * @param expression a boolean expression
     * @param exceptionSupplier supplier of the exception to be thrown
     * @throws X if {@code expression} is false
     */
    public static <X extends Throwable> void check(boolean expression, Supplier<? extends X> exceptionSupplier) throws X {
        if (!expression)
            throw exceptionSupplier.get();
    }
}
