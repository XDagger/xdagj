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

import java.util.ArrayList;
import java.util.List;

import io.xdag.crypto.Hash;

/**
 * Simple implementation of the Merkle tree.
 */
public class MerkleTree {

    private final Node root;
    private final int size;
    private int levels = 0;

    /**
     * Construct a Merkle tree.
     */
    public MerkleTree(List<byte[]> hashes) {
        this.size = hashes.size();

        List<Node> nodes = new ArrayList<>();
        for (byte[] b : hashes) {
            nodes.add(new Node(b));
        }
        this.root = build(nodes);
    }

    /**
     * Get the root hash.
     */
    public byte[] getRootHash() {
        return root.value;
    }

    /**
     * Get the size of elements.
     */
    public int size() {
        return size;
    }

    /**
     * Returns the Merkle proof of the Nth element.
     * 
     * @param i
     *            the element index, starting from zero.
     */
    public List<byte[]> getProof(int i) {
        List<byte[]> proof = new ArrayList<>();

        int half = 1 << (levels - 2);
        Node p = root;
        do {
            // shallow copy
            proof.add(p.value);

            if (i < half) {
                p = p.left;
            } else {
                p = p.right;
            }
            i -= half;
            half >>= 1;
        } while (p != null);

        return proof;
    }

    private Node build(List<Node> nodes) {
        if (nodes.isEmpty()) {
            return new Node(BytesUtils.EMPTY_HASH);
        }

        while (nodes.size() > 1) {
            List<Node> list = new ArrayList<>();

            for (int i = 0; i < nodes.size(); i += 2) {
                Node left = nodes.get(i);
                if (i + 1 < nodes.size()) {
                    Node right = nodes.get(i + 1);
                    list.add(new Node(Hash.h256(left.value, right.value), left, right));
                } else {
                    list.add(new Node(left.value, left, null));
                }
            }

            levels++;
            nodes = list;
        }

        levels++;
        return nodes.get(0);
    }

    private static class Node {
        final byte[] value;
        Node left;
        Node right;

        public Node(byte[] value) {
            this.value = value;
        }

        public Node(byte[] value, Node left, Node right) {
            this.value = value;
            this.left = left;
            this.right = right;
        }
    }
}
