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

import org.apache.tuweni.bytes.MutableBytes;

import io.xdag.net.XdagVersion;
import io.xdag.net.message.Message;
import io.xdag.net.message.MessageFactory;
import io.xdag.net.message.XdagMessageCodes;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MinerMessageFactory implements MessageFactory {

    @Override
    public Message create(byte code, MutableBytes encoded) {
        // 从当前版本中获取到有用的信息
        XdagMessageCodes receivedCommand = XdagMessageCodes.fromByte(code, XdagVersion.V03);
        switch (receivedCommand) {
            case TASK_SHARE -> {
                return new TaskShareMessage(encoded);
            }
            case NEW_TASK -> {
                return new NewTaskMessage(encoded);
            }
            case NEW_BALANCE -> {
                return new NewBalanceMessage(encoded);
            }
            case WORKER_NAME -> {
                return new WorkerNameMessage(encoded);
            }
            default -> {
                log.debug(encoded.toHexString());
                throw new IllegalArgumentException("No such message code" + receivedCommand);
            }
        }
    }
}
