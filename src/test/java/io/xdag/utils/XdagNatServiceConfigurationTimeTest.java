
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

