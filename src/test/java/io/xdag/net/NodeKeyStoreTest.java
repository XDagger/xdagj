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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.xdag.crypto.keys.ECKeyPair;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NodeKeyStoreTest {

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    @Test
    public void keyIsCreatedOnceAndLoadedAfterwards() throws IOException {
        Path file = new File(root.getRoot(), "sub/node.key").toPath();
        ECKeyPair first = NodeKeyStore.loadOrCreate(file);
        assertTrue(Files.exists(file));
        ECKeyPair second = NodeKeyStore.loadOrCreate(file);
        assertEquals(first.toBase58Address(), second.toBase58Address());
        assertEquals(first.getPrivateKey().toBytes(), second.getPrivateKey().toBytes());
    }

    @Test
    public void damagedKeyFileIsReported() throws IOException {
        Path file = new File(root.getRoot(), "node.key").toPath();
        Files.writeString(file, "not a key");
        try {
            NodeKeyStore.loadOrCreate(file);
            fail("a damaged key file must not be silently replaced");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("node.key"));
        }
    }
}
