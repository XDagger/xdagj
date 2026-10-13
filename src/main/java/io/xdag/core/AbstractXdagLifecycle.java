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

package io.xdag.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class that implements common lifecycle management logic.
 * Subclasses only need to implement doStart() and doStop() methods.
 */
public abstract class AbstractXdagLifecycle implements XdagLifecycle {

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public final void start() {
        if (running.compareAndSet(false, true)) {
            doStart();
        }
    }

    @Override
    public final void stop() {
        if (running.compareAndSet(true, false)) {
            doStop();
        }
    }

    @Override
    public final boolean isRunning() {
        return running.get();
    }

    /**
     * Template method for subclasses to implement start logic.
     * This method is called only when the service is not running.
     */
    protected abstract void doStart();

    /**
     * Template method for subclasses to implement stop logic.
     * This method is called only when the service is running.
     */
    protected abstract void doStop();
} 