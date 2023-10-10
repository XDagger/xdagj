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

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SystemUtils {

    // Fix JNA issue: There is an incompatible JNA native library installed
    static {
        System.setProperty("jna.nosys", "true");
    }

    public static class Code {
        // success
        public static final int OK = 0;

        // wallet
        public static final int FAILED_TO_WRITE_WALLET_FILE = 11;
        public static final int FAILED_TO_UNLOCK_WALLET = 12;
        public static final int ACCOUNT_NOT_EXIST = 13;
        public static final int ACCOUNT_ALREADY_EXISTS = 14;
        public static final int INVALID_PRIVATE_KEY = 15;
        public static final int WALLET_LOCKED = 16;
        public static final int PASSWORD_REPEAT_NOT_MATCH = 17;
        public static final int WALLET_ALREADY_EXISTS = 18;
        public static final int WALLET_ALREADY_UNLOCKED = 19;
        public static final int FAILED_TO_INIT_HD_WALLET = 20;

        // kernel
        public static final int FAILED_TO_INIT_ED25519 = 31;
        public static final int FAILED_TO_LOAD_CONFIG = 32;
        public static final int FAILED_TO_LOAD_GENESIS = 33;
        public static final int FAILED_TO_LAUNCH_KERNEL = 34;
        public static final int INVALID_NETWORK_LABEL = 35;
        public static final int FAILED_TO_SETUP_TRACER = 36;

        // database
        public static final int FAILED_TO_OPEN_DB = 51;
        public static final int FAILED_TO_REPAIR_DB = 52;
        public static final int FAILED_TO_WRITE_BATCH_TO_DB = 53;

        // upgrade
        public static final int HARDWARE_UPGRADE_NEEDED = 71;
        public static final int CLIENT_UPGRADE_NEEDED = 72;
        public static final int JVM_32_NOT_SUPPORTED = 73;
    }

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
     * Returns the operating system name.
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
     */
    public static String getOsArch() {
        return System.getProperty("os.arch");
    }

    /**
     * Returns whether the JVM is in 32-bit data model
     */
    public static boolean is32bitJvm() {
        String model = System.getProperty("sun.arch.data.model");
        return model != null && model.contains("32");
    }

    /**
     * Returns whether the JVM is in 64-bit data model
     */
    public static boolean is64bitJvm() {
        String model = System.getProperty("sun.arch.data.model");
        return model != null && model.contains("64");
    }

    /**
     * Terminates the JVM synchronously.
     */
    public static void exit(int code) {
        System.exit(code);
    }

    /**
     * Terminates the JVM asynchronously.
     */
    public static void exitAsync(int code) {
        new Thread(() -> System.exit(code)).start();
    }

    /**
     * Returns the number of processors.
     */
    public static int getNumberOfProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Returns the size of heap in use.
     */
    public static long getUsedHeapSize() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Change localization.
     *
     * @param locale
     *            the target localization.
     */
    public static void setLocale(Locale locale) {
        try {
            if (!Locale.getDefault().equals(locale)) {
                Locale.setDefault(locale);
            }
        } catch (SecurityException e) {
            log.error("Unable to change localization.", e);
        }
    }

}
