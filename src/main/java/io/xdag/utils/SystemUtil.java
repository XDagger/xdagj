package io.xdag.utils;

import java.util.Locale;

public class SystemUtil {

    public enum OsName {
        WINDOWS("Windows"),

        LINUX("Linux"),

        MACOS("macOS"),

        UNKNOWN("Unknown");

        private final String name;

        OsName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Returns whether the JVM is in 32-bit data model
     *
     * @return
     */
    public static boolean is32bitJvm() {
        String model = System.getProperty("sun.arch.data.model");
        return model != null && model.contains("32");
    }

    /**
     * Returns the operating system name.
     *
     * @return
     */
    public static OsName getOsName() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            return OsName.WINDOWS;
        } else if (os.contains("linux")) {
            return OsName.LINUX;
        } else if (os.contains("mac")) {
            return OsName.MACOS;
        } else {
            return OsName.UNKNOWN;
        }
    }

    /**
     * Returns the operating system architecture
     *
     * @return
     */
    public static String getOsArch() {
        return System.getProperty("os.arch");
    }

}
