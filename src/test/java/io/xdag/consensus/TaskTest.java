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
import org.apache.tuweni.bytes.MutableBytes;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TaskTest {

    @Test
    public void testTaskClone() throws CloneNotSupportedException {
        Task t = new Task();

        XdagField[] xfArray = new XdagField[2];
        xfArray[0] = new XdagField(MutableBytes.EMPTY);
        xfArray[0].setSum(1);
        xfArray[0].setType(XdagField.FieldType.XDAG_FIELD_HEAD);

        xfArray[1] = new XdagField(MutableBytes.EMPTY);
        xfArray[1].setSum(2);
        xfArray[1].setType(XdagField.FieldType.XDAG_FIELD_IN);

        t.setTask(xfArray);
        t.setDigest(new XdagSha256Digest());
        t.setTaskTime(System.currentTimeMillis());
        t.setTaskIndex(1);

        Task cloneTask = (Task)t.clone();

        assertNotEquals(t, cloneTask);
        assertNotEquals(t.getTask(), cloneTask.getTask());
        assertNotEquals(t.getDigest(), cloneTask.getDigest());
        assertNotEquals(t.getTask()[0], cloneTask.getTask()[0]);
        assertNotEquals(t.getTask()[1], cloneTask.getTask()[1]);

        assertEquals(t.getTaskTime(), cloneTask.getTaskTime());
        assertEquals(t.getTaskIndex(), cloneTask.getTaskIndex());
        assertEquals(t.getTask()[0].getSum(), cloneTask.getTask()[0].getSum());
        assertEquals(t.getTask()[0].getType(), cloneTask.getTask()[0].getType());
        assertEquals(t.getTask()[1].getSum(), cloneTask.getTask()[1].getSum());
        assertEquals(t.getTask()[1].getType(), cloneTask.getTask()[1].getType());
    }

}
