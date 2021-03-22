package io.xdag.crypto.bip32;

import io.xdag.crypto.bip44.ChildNumber;
import io.xdag.crypto.bip44.HDDerivationException;
import io.xdag.crypto.bip44.HDKeyDerivation;
import io.xdag.crypto.bip44.HDPath;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public class DeterministicHierarchy {

    private final Map<HDPath, DeterministicKey> keys = new HashMap<>();
    private final HDPath rootPath;
    // Keep track of how many child keys each node has. This is kind of weak.
    private final Map<HDPath, ChildNumber> lastChildNumbers = new HashMap<>();

    /**
     * Constructs a new hierarchy rooted at the given key. Note that this does not have to be the top of the tree.
     * You can construct a DeterministicHierarchy for a subtree of a larger tree that you may not own.
     */
    public DeterministicHierarchy(DeterministicKey rootKey) {
        putKey(rootKey);
        rootPath = rootKey.getPath();
    }

    /**
     * Inserts a key into the hierarchy. Used during deserialization: you normally don't need this. Keys must be
     * inserted in order.
     */
    public final void putKey(DeterministicKey key) {
        HDPath path = key.getPath();
        // Update our tracking of what the next child in each branch of the tree should be. Just assume that keys are
        // inserted in order here.
        final DeterministicKey parent = key.getParent();
        if (parent != null)
            lastChildNumbers.put(parent.getPath(), key.getChildNumber());
        keys.put(path, key);
    }

    /**
     * Returns a key for the given path, optionally creating it.
     *
     * @param path the path to the key
     * @param relativePath whether the path is relative to the root path
     * @param create whether the key corresponding to path should be created (with any necessary ancestors) if it doesn't exist already
     * @return next newly created key using the child derivation function
     * @throws IllegalArgumentException if create is false and the path was not found.
     */
    public DeterministicKey get(List<ChildNumber> path, boolean relativePath, boolean create) {
        HDPath inputPath = HDPath.M(path);
        HDPath absolutePath = relativePath
                ? rootPath.extend(path)
                : inputPath;
        if (!keys.containsKey(absolutePath)) {
            if (!create)
                throw new IllegalArgumentException(String.format(Locale.US, "No key found for %s path %s.",
                        relativePath ? "relative" : "absolute", inputPath.toString()));
            checkArgument(absolutePath.size() > 0, "Can't derive the master key: nothing to derive from.");
            DeterministicKey parent = get(absolutePath.subList(0, absolutePath.size() - 1), false, true);
            putKey(HDKeyDerivation.deriveChildKey(parent, absolutePath.get(absolutePath.size() - 1)));
        }
        return keys.get(absolutePath);
    }

    /**
     * Extends the tree by calculating the next key that hangs off the given parent path. For example, if you pass a
     * path of 1/2 here and there are already keys 1/2/1 and 1/2/2 then it will derive 1/2/3.
     *
     * @param parentPath the path to the parent
     * @param relative whether the path is relative to the root path
     * @param createParent whether the parent corresponding to path should be created (with any necessary ancestors) if it doesn't exist already
     * @param privateDerivation whether to use private or public derivation
     * @return next newly created key using the child derivation function
     * @throws IllegalArgumentException if the parent doesn't exist and createParent is false.
     */
    public DeterministicKey deriveNextChild(List<ChildNumber> parentPath, boolean relative, boolean createParent, boolean privateDerivation) {
        DeterministicKey parent = get(parentPath, relative, createParent);
        int nAttempts = 0;
        while (nAttempts++ < HDKeyDerivation.MAX_CHILD_DERIVATION_ATTEMPTS) {
            try {
                ChildNumber createChildNumber = getNextChildNumberToDerive(parent.getPath(), privateDerivation);
                return deriveChild(parent, createChildNumber);
            } catch (HDDerivationException ignore) { }
        }
        throw new HDDerivationException("Maximum number of child derivation attempts reached, this is probably an indication of a bug.");
    }

    private ChildNumber getNextChildNumberToDerive(HDPath path, boolean privateDerivation) {
        ChildNumber lastChildNumber = lastChildNumbers.get(path);
        ChildNumber nextChildNumber = new ChildNumber(lastChildNumber != null ? lastChildNumber.num() + 1 : 0, privateDerivation);
        lastChildNumbers.put(path, nextChildNumber);
        return nextChildNumber;
    }

    public int getNumChildren(HDPath path) {
        final ChildNumber cn = lastChildNumbers.get(path);
        if (cn == null)
            return 0;
        else
            return cn.num() + 1;   // children start with zero based childnumbers
    }

    /**
     * Extends the tree by calculating the requested child for the given path. For example, to get the key at position
     * 1/2/3 you would pass 1/2 as the parent path and 3 as the child number.
     *
     * @param parentPath the path to the parent
     * @param relative whether the path is relative to the root path
     * @param createParent whether the parent corresponding to path should be created (with any necessary ancestors) if it doesn't exist already
     * @return the requested key.
     * @throws IllegalArgumentException if the parent doesn't exist and createParent is false.
     */
    public DeterministicKey deriveChild(List<ChildNumber> parentPath, boolean relative, boolean createParent, ChildNumber createChildNumber) {
        return deriveChild(get(parentPath, relative, createParent), createChildNumber);
    }

    private DeterministicKey deriveChild(DeterministicKey parent, ChildNumber createChildNumber) {
        DeterministicKey childKey = HDKeyDerivation.deriveChildKey(parent, createChildNumber);
        putKey(childKey);
        return childKey;
    }

    /**
     * Returns the root key that the {@link DeterministicHierarchy} was created with.
     */
    public DeterministicKey getRootKey() {
        return get(rootPath, false, false);
    }

}
