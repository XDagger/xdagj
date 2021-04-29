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
package io.xdag.utils;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.Test;

import java.text.ParseException;
import java.util.Date;
import static org.junit.Assert.*;

public class XdagNatServiceConfigurationTimeTest {

    @Test
    public void testGetEndOfEpoch() throws ParseException {
        Date date1 = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss").parse("2020-09-20 23:00:00");
        Date date2 = new Date(XdagTime.getEndOfEpoch(date1.getTime()));
        for(int i = 0 ; i < 10; i++) {
            long epoch2 = XdagTime.getEpoch(date2.getTime());
            Date date3 = DateUtils.addSeconds(date2, 64);
            long epoch3 = XdagTime.getEpoch(date3.getTime());
            assertTrue(epoch3 > epoch2);
        }

    }
}
