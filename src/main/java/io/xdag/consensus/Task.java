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

package io.xdag.consensus;

import io.xdag.core.XdagField;
import io.xdag.utils.XdagSha256Digest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Task implements Cloneable {
    private XdagField[] task;
    private static final int TASK_FLAG = 1;
    private long taskTime;

    private long taskIndex;

    private XdagSha256Digest digest;

    @Override
    public String toString() {
        return "Task:{ taskTime:" + taskTime + ", taskIndex:" + taskIndex + ", digest:" + (digest != null ?
                digest.toString() : "null") +
                "}";
    }

    public String toJsonString() {
        String preHash = "";
        String taskSeed = "";
        if (task != null && task.length == 2) {
            preHash = task[0].getData().toUnprefixedHexString();
            taskSeed = task[1].getData().toUnprefixedHexString();
        }
        return "{\n" +
                "  \"msgType\": " + TASK_FLAG + ",\n" +
                "  \"msgContent\": {\n" +
                "    \"task\": {\n" +
                "      \"preHash\": \"" + preHash + "\",\n" +
                "      \"taskSeed\": \"" + taskSeed + "\"\n" +
                "    },\n" +
                "    \"taskTime\": " + taskTime + ",\n" +
                "    \"taskIndex\": " + taskIndex + "\n" +
                "  }\n" +
                "}";
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        Task t = (Task) super.clone();
        if (task != null && task.length > 0) {
            XdagField[] xfArray = new XdagField[task.length];
            for (int i = 0; i < t.getTask().length; i++) {
                xfArray[i] = (XdagField) (t.getTask()[i]).clone();
            }
            t.setTask(xfArray);
        }
        t.digest = new XdagSha256Digest(digest);
        return t;
    }
}
