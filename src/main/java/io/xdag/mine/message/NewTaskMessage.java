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
package io.xdag.mine.message;

import static io.xdag.net.message.XdagMessageCodes.NEW_TASK;

import io.xdag.core.XdagField;
import io.xdag.net.message.Message;
import io.xdag.net.message.XdagMessageCodes;
import io.xdag.utils.BytesUtils;

public class NewTaskMessage extends Message {

    private final XdagField[] xdagFields = new XdagField[2];

    public NewTaskMessage(byte[] bytes) {
        super(bytes);
        xdagFields[0] = new XdagField(BytesUtils.subArray(bytes, 0, 32));
        xdagFields[1] = new XdagField(BytesUtils.subArray(bytes, 32, 32));
    }

    @Override
    public byte[] getEncoded() {

        byte[] data = new byte[64];
        System.arraycopy(xdagFields[0].getData(), 0, data, 0, 32);
        System.arraycopy(xdagFields[1].getData(), 0, data, 32, 32);

        return data;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return NEW_TASK;
    }

    @Override
    public String toString() {
        return null;
    }
}
