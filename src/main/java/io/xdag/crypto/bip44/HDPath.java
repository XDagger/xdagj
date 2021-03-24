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
package io.xdag.crypto.bip44;

import com.google.common.base.Splitter;

import javax.annotation.Nonnull;
import java.util.*;

public class HDPath extends AbstractList<ChildNumber> {

    private static final char PREFIX_PRIVATE = 'm';
    private static final char PREFIX_PUBLIC = 'M';
    private static final char SEPARATOR = '/';
    private static final Splitter SEPARATOR_SPLITTER = Splitter.on(SEPARATOR).trimResults();
    protected final boolean hasPrivateKey;
    protected final List<ChildNumber> unmodifiableList;

    /**
     * Constructs a path for a public or private key.
     *
     * @param hasPrivateKey Whether it is a path to a private key or not
     * @param list List of children in the path
     */
    public HDPath(boolean hasPrivateKey, List<ChildNumber> list) {
        this.hasPrivateKey = hasPrivateKey;
        this.unmodifiableList = Collections.unmodifiableList(list);
    }

    /**
     * Constructs a path for a public key.
     *
     * @param list List of children in the path
     */
    public HDPath(List<ChildNumber> list) {
        this(false, list);
    }

    /**
     * Returns a path for a public or private key.
     *
     * @param hasPrivateKey Whether it is a path to a private key or not
     * @param list List of children in the path
     */
    private static HDPath of(boolean hasPrivateKey, List<ChildNumber> list) {
        return new HDPath(hasPrivateKey, list);
    }

    /**
     * Returns a path for a public key.
     *
     * @param list List of children in the path
     */
    public static HDPath M(List<ChildNumber> list) {
        return HDPath.of(false, list);
    }

    /**
     * Returns an empty path for a public key.
     */
    public static HDPath M() {
        return HDPath.M(Collections.<ChildNumber>emptyList());
    }

    /**
     * Returns a path for a public key.
     *
     * @param childNumber Single child in path
     */
    public static HDPath M(ChildNumber childNumber) {
        return HDPath.M(Collections.singletonList(childNumber));
    }

    /**
     * Returns a path for a public key.
     *
     * @param children Children in the path
     */
    public static HDPath M(ChildNumber... children) {
        return HDPath.M(Arrays.asList(children));
    }

    /**
     * Returns a path for a private key.
     *
     * @param list List of children in the path
     */
    public static HDPath m(List<ChildNumber> list) {
        return HDPath.of(true, list);
    }

    /**
     * Returns an empty path for a private key.
     */
    public static HDPath m() {
        return HDPath.m(Collections.<ChildNumber>emptyList());
    }

    /**
     * Returns a path for a private key.
     *
     * @param childNumber Single child in path
     */
    public static HDPath m(ChildNumber childNumber) {
        return HDPath.m(Collections.singletonList(childNumber));
    }

    /**
     * Returns a path for a private key.
     *
     * @param children Children in the path
     */
    public static HDPath m(ChildNumber... children) {
        return HDPath.m(Arrays.asList(children));
    }

    /**
     * Create an HDPath from a path string. The path string is a human-friendly representation of the deterministic path. For example:
     *
     * "44H / 0H / 0H / 1 / 1"
     *
     * Where a letter "H" means hardened key. Spaces are ignored.
     */
    public static HDPath parsePath(@Nonnull String path) {
        List<String> parsedNodes = new LinkedList<>(SEPARATOR_SPLITTER.splitToList(path));
        boolean hasPrivateKey = false;
        if (!parsedNodes.isEmpty()) {
            final String firstNode = parsedNodes.get(0);
            if (firstNode.equals(Character.toString(PREFIX_PRIVATE)))
                hasPrivateKey = true;
            if (hasPrivateKey || firstNode.equals(Character.toString(PREFIX_PUBLIC)))
                parsedNodes.remove(0);
        }
        List<ChildNumber> nodes = new ArrayList<>(parsedNodes.size());

        for (String n : parsedNodes) {
            if (n.isEmpty()) continue;
            boolean isHard = n.endsWith("H");
            if (isHard) n = n.substring(0, n.length() - 1).trim();
            int nodeNumber = Integer.parseInt(n);
            nodes.add(new ChildNumber(nodeNumber, isHard));
        }

        return new HDPath(hasPrivateKey, nodes);
    }

    /**
     * Is this a path to a private key?
     *
     * @return true if yes, false if no or a partial path
     */
    public boolean hasPrivateKey() {
        return hasPrivateKey;
    }

    /**
     * Extend the path by appending additional ChildNumber objects.
     *
     * @param child1 the first child to append
     * @param children zero or more additional children to append
     * @return A new immutable path
     */
    public HDPath extend(ChildNumber child1, ChildNumber... children) {
        List<ChildNumber> mutable = new ArrayList<>(this.unmodifiableList); // Mutable copy
        mutable.add(child1);
        mutable.addAll(Arrays.asList(children));
        return new HDPath(this.hasPrivateKey, mutable);
    }

    /**
     * Extend the path by appending a relative path.
     *
     * @param path2 the relative path to append
     * @return A new immutable path
     */
    public HDPath extend(HDPath path2) {
        List<ChildNumber> mutable = new ArrayList<>(this.unmodifiableList); // Mutable copy
        mutable.addAll(path2);
        return new HDPath(this.hasPrivateKey, mutable);
    }

    /**
     * Extend the path by appending a relative path.
     *
     * @param path2 the relative path to append
     * @return A new immutable path
     */
    public HDPath extend(List<ChildNumber> path2) {
        return this.extend(HDPath.M(path2));
    }

    @Override
    public ChildNumber get(int index) {
        return unmodifiableList.get(index);
    }

    @Override
    public int size() {
        return unmodifiableList.size();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(hasPrivateKey ? HDPath.PREFIX_PRIVATE : HDPath.PREFIX_PUBLIC);
        for (ChildNumber segment : unmodifiableList) {
            b.append(HDPath.SEPARATOR);
            b.append(segment.toString());
        }
        return b.toString();
    }

}
