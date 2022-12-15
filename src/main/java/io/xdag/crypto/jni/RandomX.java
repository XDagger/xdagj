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

package io.xdag.crypto.jni;

import io.xdag.utils.SystemUtil;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomX {

    private static final Logger logger = LoggerFactory.getLogger(RandomX.class);

    protected static File nativeDir;
    protected static boolean enabled = false;

    // initialize library when the class loads
    static {
        init();
    }

    /**
     * Initializes the native libraries
     */
    protected static void init() {
        if (SystemUtil.is32bitJvm()) {
            // No more support for 32-bit systems
            return;
        }

        SystemUtil.OsName os = SystemUtil.getOsName();
        switch (os) {
            case LINUX -> {
                if (SystemUtil.getOsArch().equals("aarch64")) {
                    enabled = loadLibrary("/native/Linux-aarch64/librandomx.so");
                } else {
                    enabled = loadLibrary("/native/Linux-x86_64/librandomx.so");
                }
            }
            case MACOS -> enabled = loadLibrary("/native/Darwin-x86_64/librandomx.dylib");
            case WINDOWS -> enabled = loadLibrary("/native/Windows-x86_64/librandomx.dll");
            default -> {
            }
        }
    }

    /**
     * Loads a library file from bundled resource.
     */
    protected static boolean loadLibrary(String resource) {
        try {
            if (nativeDir == null) {
                nativeDir = Files.createTempDirectory("native").toFile();
                nativeDir.deleteOnExit();
            }

            String name = resource.contains("/") ? resource.substring(resource.lastIndexOf('/') + 1) : resource;
            File file = new File(nativeDir, name);

            if (!file.exists()) {
                InputStream in = Native.class.getResourceAsStream(resource); // null pointer exception
                OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                for (int c; (c = Objects.requireNonNull(in).read()) != -1; ) {
                    out.write(c);
                }
                out.close();
                in.close();
            }

            System.load(file.getAbsolutePath());
            return true;
        } catch (Exception | UnsatisfiedLinkError e) {
            logger.warn("Failed to load native library: {}", resource, e);
            return false;
        }
    }

    public static native long allocCache();

    public static native long initCache(long cache, byte[] key, int len);

    public static native void releaseCache(long cache);

    public static native long allocDataSet();

    public static native long initDataSet(long cache, long dataset, int miners);

    public static native void releaseDataSet(long dataset);

    public static native long createVm(long cache, long dataset, int miners);

    public static native long destroyVm(long vm);

    public static native byte[] calculateHash(long vm, byte[] data, int length);
}
