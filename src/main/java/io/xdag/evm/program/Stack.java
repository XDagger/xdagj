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
package io.xdag.evm.program;

import io.xdag.evm.DataWord;

/**
 * Program runtime stack.
 */
public class Stack {

    private final java.util.Stack<DataWord> stack = new java.util.Stack<>();

    public synchronized DataWord pop() {
        return stack.pop();
    }

    public void push(DataWord item) {
        stack.push(item);
    }

    public void swap(int from, int to) {
        if (isAccessible(from) && isAccessible(to) && (from != to)) {
            DataWord tmp = stack.get(from);
            stack.set(from, stack.set(to, tmp));
        }
    }

    public DataWord peek() {
        return stack.peek();
    }

    public DataWord get(int index) {
        return stack.get(index);
    }

    public int size() {
        return stack.size();
    }

    private boolean isAccessible(int from) {
        return from >= 0 && from < stack.size();
    }

    public DataWord[] toArray() {
        return stack.toArray(new DataWord[0]);
    }
}
