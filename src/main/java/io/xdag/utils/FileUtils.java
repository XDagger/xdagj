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

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import com.google.common.collect.Lists;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class FileUtils {

    /**
     * sum file name
     */
//    public static List<String> getFileName(long time) {
//        List<String> files = Lists.newArrayList(BlockStore.SUM_FILE_NAME);
//        StringBuilder stringBuffer = new StringBuilder(
//                Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 40) & 0xff), true)));
//        stringBuffer.append("/");
//        files.add(stringBuffer + BlockStore.SUM_FILE_NAME);
//        stringBuffer.append(Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 32) & 0xff), true)));
//        stringBuffer.append("/");
//        files.add(stringBuffer + BlockStore.SUM_FILE_NAME);
//        stringBuffer.append(Hex.toHexString(BytesUtils.byteToBytes((byte) ((time >> 24) & 0xff), true)));
//        stringBuffer.append("/");
//        files.add(stringBuffer + BlockStore.SUM_FILE_NAME);
//        return files;
//    }

    public static final Set<PosixFilePermission> POSIX_SECURED_PERMISSIONS = Set.of(OWNER_READ, OWNER_WRITE);

    /**
     * Delete a file or directory recursively.
     */
    public static void recursiveDelete(File file) {
        try {
            Files.walk(file.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", file, e);
        }
    }

    /**
     * Check if the file's permission is secure.
     */
    public static boolean isPosixPermissionSecured(File file) throws IOException {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file.toPath());
        return permissions.containsAll(POSIX_SECURED_PERMISSIONS) && POSIX_SECURED_PERMISSIONS.containsAll(permissions);
    }

}
