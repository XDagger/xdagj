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

import java.util.HashSet;
import java.util.Set;

import io.xdag.evm.OpCode;
import org.apache.tuweni.bytes.Bytes;

/**
 * Pre-compile the program code to speed up execution.
 *
 * Features included:
 * <ul>
 * <li>Collect the list of JUMP destinations</li>
 * </ul>
 */
public class ProgramPreprocess {
    private final Set<Integer> jumpdest = new HashSet<>();

    public boolean hasJumpDest(int pc) {
        return jumpdest.contains(pc);
    }

    public static ProgramPreprocess compile(Bytes ops) {
        ProgramPreprocess ret = new ProgramPreprocess();

        for (int i = 0; i < ops.size(); ++i) {
            OpCode op = OpCode.code(ops.get(i));
            if (op == null) {
                continue;
            }

            if (op.equals(OpCode.JUMPDEST)) {
                ret.jumpdest.add(i);
            }

            if (op.asInt() >= OpCode.PUSH1.asInt() && op.asInt() <= OpCode.PUSH32.asInt()) {
                i += op.asInt() - OpCode.PUSH1.asInt() + 1;
            }
        }

        return ret;
    }
}
