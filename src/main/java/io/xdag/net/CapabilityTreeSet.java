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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A TreeSet implementation for managing protocol capabilities
 */
public class CapabilityTreeSet {

    private final TreeSet<Capability> capabilities;

    private CapabilityTreeSet(Collection<Capability> capabilities) {
        this.capabilities = new TreeSet<>(capabilities);
    }

    /**
     * Creates an empty capability set
     *
     * @return An empty CapabilityTreeSet
     */
    public static CapabilityTreeSet emptyList() {
        return new CapabilityTreeSet(Collections.emptyList());
    }

    /**
     * Creates a capability set from an array of Capability objects
     *
     * @param capabilities Array of capabilities to include
     * @return A new CapabilityTreeSet containing the specified capabilities
     */
    public static CapabilityTreeSet of(Capability... capabilities) {
        return new CapabilityTreeSet(Stream.of(capabilities).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    /**
     * Creates a capability set from an array of capability names
     *
     * @param capabilities Array of capability names
     * @return A new CapabilityTreeSet containing the valid capabilities
     * @note Invalid capability names are ignored
     */
    public static CapabilityTreeSet of(String... capabilities) {
        return new CapabilityTreeSet(
                Stream.of(capabilities).map(Capability::of).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    /**
     * Checks if a specific capability is supported
     *
     * @param capability The capability to check
     * @return true if the capability is supported, false otherwise
     */
    public boolean isSupported(Capability capability) {
        return capabilities.contains(capability);
    }

    /**
     * Gets the number of capabilities in the set
     *
     * @return The size of the capability set
     */
    public int size() {
        return capabilities.size();
    }

    /**
     * Converts the capability set to an array of capability names
     *
     * @return Array of capability names as strings
     */
    public String[] toArray() {
        return capabilities.stream().map(Capability::name).toArray(String[]::new);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof CapabilityTreeSet
                && Arrays.equals(toArray(), ((CapabilityTreeSet) object).toArray());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(toArray());
    }

}
