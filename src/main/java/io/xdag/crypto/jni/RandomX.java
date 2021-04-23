package io.xdag.crypto.jni;

import io.xdag.utils.SystemUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;

public class RandomX {
    private static final Logger logger = LoggerFactory.getLogger(RandomX.class);

    protected static File nativeDir;
    protected static boolean enabled = false;

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
            case LINUX:
                if (SystemUtil.getOsArch().equals("aarch64")) {
                    enabled = loadLibrary("/native/Linux-aarch64/librandomx.so");
                } else {
                    enabled = loadLibrary("/native/Linux-x86_64/librandomx.so");
                }
                break;
            case MACOS:
                enabled = loadLibrary("/native/Darwin-x86_64/librandomx.dylib");
                break;
            case WINDOWS:
                enabled = loadLibrary("/native/Windows-x86_64/librandomx.dll");
                break;
            default:
                break;
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
                for (int c; (c = in.read()) != -1;) {
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

    // initialize library when the class loads
    static {
        init();
    }

    public static native long allocCache();
    public static native long initCache(long cache,byte[] key,int len);
    public static native void releaseCache(long cache);
    public static native long allocDataSet();
    public static native long initDataSet(long cache,long dataset,int miners);
    public static native void releaseDataSet(long dataset);
    public static native long createVm(long cache,long dataset,int miners);
    public static native long destroyVm(long vm);
    public static native byte[] calculateHash(long vm,byte[] data,int length);
}
