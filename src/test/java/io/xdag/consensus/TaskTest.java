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
