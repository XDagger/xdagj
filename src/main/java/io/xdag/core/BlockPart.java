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

import java.util.ArrayList;
import java.util.List;

public enum BlockPart {
    HEADER(1), TRANSACTIONS(1 << 1), RESULTS(1 << 2);

    private final int code;

    BlockPart(int code) {
        this.code = code;
    }

    public static int encode(BlockPart... parts) {
        int result = 0;
        for (BlockPart part : parts) {
            result |= part.code;
        }
        return result;
    }

    public static List<BlockPart> decode(int parts) {
        List<BlockPart> result = new ArrayList<>();
        // NOTE: values() returns an array containing all the values of the enum type
        // in the order they are declared.
        for (BlockPart bp : BlockPart.values()) {
            if ((parts & bp.code) != 0) {
                result.add(bp);
            }
        }

        return result;
    }
}
