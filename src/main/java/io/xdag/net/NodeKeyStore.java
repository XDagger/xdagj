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
package io.xdag.net;

import io.xdag.crypto.keys.ECKeyPair;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes32;

/**
 * The node's network identity: a key pair that is created once and kept in a file, separate from the wallet.
 * Other nodes know this node by the address of this key (in the handshake and in discovery); the wallet key
 * signs blocks and never leaves the wallet.
 */
@Slf4j
public final class NodeKeyStore {

    private NodeKeyStore() {
    }

    /** Loads the key from the file, creating a new one there if the file does not exist. */
    public static ECKeyPair loadOrCreate(Path file) throws IOException {
        if (Files.exists(file)) {
            String hex = Files.readString(file, StandardCharsets.UTF_8).trim();
            try {
                return ECKeyPair.fromBytes(Bytes32.fromHexString(hex.startsWith("0x") ? hex : "0x" + hex));
            } catch (RuntimeException e) {
                throw new IOException("node key file " + file + " is not a valid key", e);
            }
        }
        ECKeyPair key = ECKeyPair.generate();
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, key.getPrivateKey().toBytes().toUnprefixedHexString() + System.lineSeparator(),
                StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("Cannot restrict permissions of {}: {}", file, e.toString());
        }
        log.info("Created node key {} (node id {})", file, key.toBase58Address());
        return key;
    }
}
