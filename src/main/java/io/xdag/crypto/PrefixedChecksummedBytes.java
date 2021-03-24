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
package io.xdag.crypto;

import io.xdag.config.Config;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;

import static com.google.common.base.Preconditions.*;

/**
 * <p>
 * The following format is often used to represent some type of data (e.g. key or hash of key):
 * </p>
 *
 * <pre>
 * [prefix] [data bytes] [checksum]
 * </pre>
 * <p>
 * and the result is then encoded with some variant of base. This format is most commonly used for addresses and private
 * keys exported using Bitcoin Core's dumpprivkey command.
 * </p>
 */
public abstract class PrefixedChecksummedBytes implements Serializable, Cloneable {
    protected transient Config config;
    protected final byte[] bytes;

    protected PrefixedChecksummedBytes(Config config, byte[] bytes) {
        this.config = checkNotNull(config);
        this.bytes = checkNotNull(bytes);
    }

    /**
     * @return network this data is valid for
     */
    public final Config getConfig() {
        return config;
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, Arrays.hashCode(bytes));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrefixedChecksummedBytes other = (PrefixedChecksummedBytes) o;
        return this.config.equals(other.config) && Arrays.equals(this.bytes, other.bytes);
    }

    /**
     * This implementation narrows the return type to {@link PrefixedChecksummedBytes}
     * and allows subclasses to throw {@link CloneNotSupportedException} even though it
     * is never thrown by this implementation.
     */
    @Override
    public PrefixedChecksummedBytes clone() throws CloneNotSupportedException {
        return (PrefixedChecksummedBytes) super.clone();
    }

    // Java serialization

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(config);
        //out.writeUTF(config.root);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.config = (Config)in.readObject();
//        try {
//            Field configField = PrefixedChecksummedBytes.class.getDeclaredField("config");
//            configField.setAccessible(true);
//            configField.set(this, checkNotNull(config));
//            configField.setAccessible(false);
//        } catch (NoSuchFieldException x) {
//            throw new RuntimeException(x);
//        }
    }
}

