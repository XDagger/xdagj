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

package io.xdag.net.message.impl;

import static io.xdag.net.message.XdagMessageCodes.SUMS_REQUEST;

import org.apache.commons.lang3.RandomUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

import io.xdag.core.XdagStats;
import io.xdag.net.message.AbstractMessage;
import io.xdag.net.message.NetDB;
import io.xdag.net.message.XdagMessageCodes;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
public class SumRequestMessage extends AbstractMessage {

    public SumRequestMessage(long starttime, long endtime, XdagStats xdagStats, NetDB currentDB) {
        super(SUMS_REQUEST, starttime, endtime, RandomUtils.nextLong(), xdagStats, currentDB);
        updateCrc();
    }

    public SumRequestMessage(MutableBytes bytes) {
        super(bytes);
    }

    @Override
    public Bytes getEncoded() {
        return encoded;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return SumReplyMessage.class;
    }

    @Override
    public XdagMessageCodes getCommand() {
        return XdagMessageCodes.SUMS_REQUEST;
    }

    @Override
    public String toString() {
        if (!parsed) {
            parse();
        }
        return "["
                + this.getCommand().name()
                + " starttime="
                + starttime
                + " endtime="
                + this.endtime
                + " netstatus="
                + xdagStats;
    }
}
