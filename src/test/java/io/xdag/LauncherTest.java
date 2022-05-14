package io.xdag;

import static org.junit.Assert.assertTrue;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

public class LauncherTest {

    @Test
    public void testPasswordOption() throws ParseException {
        Launcher launcher = new Launcher();
        CommandLine cmd = launcher.parseOptions(new String[]{"--password", "123"});
        assertTrue(cmd.hasOption("password"));
    }

}
