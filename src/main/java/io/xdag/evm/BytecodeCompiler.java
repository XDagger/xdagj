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
package io.xdag.evm;

import java.util.ArrayList;
import java.util.List;
import io.xdag.utils.BytesUtils;
import org.apache.tuweni.bytes.Bytes;

public class BytecodeCompiler {

    public static void main(String[] args) {
        byte[] code = BytesUtils.fromHexString(args[0]);
        System.out.println(decompile(code));
    }

    public static String decompile(byte[] code) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < code.length; i++) {
            OpCode op = OpCode.code(code[i]);
            if (op == null) {
                sb.append("[0x").append(BytesUtils.toHexString(code[i])).append("] ");
                continue;
            }

            sb.append(op.name()).append(" ");

            if (op.val() >= OpCode.PUSH1.val() && op.val() <= OpCode.PUSH32.val()) {
                int n = op.val() - OpCode.PUSH1.val() + 1;
                sb.append("0x");
                for (int j = 0; j < n && ++i < code.length; j++) {
                    sb.append(BytesUtils.toHexString(code[i]));
                }
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    public static Bytes compile(String code) {
        return Bytes.wrap(compile(code.split("\\s+")));
    }

    private static Bytes compile(String[] tokens) {
        List<Byte> bytecodes = new ArrayList<>();

        for (String t : tokens) {
            String token = t.trim().toUpperCase();

            if (token.isEmpty()) {
                continue;
            }

            if (isHexadecimal(token)) {
                compileHexadecimal(token, bytecodes);
            } else {
                bytecodes.add(OpCode.byteVal(token));
            }
        }

        byte[] bytes = new byte[bytecodes.size()];

        for (int k = 0; k < bytes.length; k++) {
            bytes[k] = bytecodes.get(k);
        }

        return Bytes.wrap(bytes);
    }

    private static boolean isHexadecimal(String token) {
        return token.startsWith("0X");
    }

    private static void compileHexadecimal(String token, List<Byte> bytecodes) {
        byte[] bytes = BytesUtils.fromHexString(token.substring(2));

        for (int k = 0; k < bytes.length; k++)
            bytecodes.add(bytes[k]);
    }
}
