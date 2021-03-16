package io.xdag.crypto.bip32;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import io.xdag.crypto.bip39.MnemonicCode;
import io.xdag.crypto.bip39.MnemonicException;
import io.xdag.utils.FormatDateUtils;
import org.apache.commons.codec.binary.Hex;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DeterministicSeed {
    // It would take more than 10^12 years to brute-force a 128 bit seed using $1B worth of computing equipment.
    public static final int DEFAULT_SEED_ENTROPY_BITS = 128;
    public static final int MAX_SEED_ENTROPY_BITS = 512;

    @Nullable
    private final byte[] seed;

    @Nullable
    private final List<String> mnemonicCode; // only one of mnemonicCode/encryptedMnemonicCode will be set
    private long creationTimeSeconds;

    public DeterministicSeed(String mnemonicString, byte[] seed, String passphrase, long creationTimeSeconds) {
        this(decodeMnemonicCode(mnemonicString), seed, passphrase, creationTimeSeconds);
    }

    public DeterministicSeed(byte[] seed, List<String> mnemonic, long creationTimeSeconds) {
        this.seed = checkNotNull(seed);
        this.mnemonicCode = checkNotNull(mnemonic);
        this.creationTimeSeconds = creationTimeSeconds;
    }

    /**
     * Constructs a seed from a BIP 39 mnemonic code. See {@link MnemonicCode} for more
     * details on this scheme.
     * @param mnemonicCode A list of words.
     * @param seed The derived seed, or pass null to derive it from mnemonicCode (slow)
     * @param passphrase A user supplied passphrase, or an empty string if there is no passphrase
     * @param creationTimeSeconds When the seed was originally created, UNIX time.
     */
    public DeterministicSeed(List<String> mnemonicCode, @Nullable byte[] seed, String passphrase, long creationTimeSeconds) {
        this((seed != null ? seed : MnemonicCode.toSeed(mnemonicCode, checkNotNull(passphrase))), mnemonicCode, creationTimeSeconds);
    }

    /**
     * Constructs a seed from a BIP 39 mnemonic code. See {@link MnemonicCode} for more
     * details on this scheme.
     * @param random Entropy source
     * @param bits number of bits, must be divisible by 32
     * @param passphrase A user supplied passphrase, or an empty string if there is no passphrase
     */
    public DeterministicSeed(SecureRandom random, int bits, String passphrase) {
        this(getEntropy(random, bits), checkNotNull(passphrase), FormatDateUtils.currentTimeSeconds());
    }

    /**
     * Constructs a seed from a BIP 39 mnemonic code. See {@link MnemonicCode} for more
     * details on this scheme.
     * @param entropy entropy bits, length must be divisible by 32
     * @param passphrase A user supplied passphrase, or an empty string if there is no passphrase
     * @param creationTimeSeconds When the seed was originally created, UNIX time.
     */
    public DeterministicSeed(byte[] entropy, String passphrase, long creationTimeSeconds) {
        checkArgument(entropy.length % 4 == 0, "entropy size in bits not divisible by 32");
        checkArgument(entropy.length * 8 >= DEFAULT_SEED_ENTROPY_BITS, "entropy size too small");
        checkNotNull(passphrase);

        try {
            this.mnemonicCode = MnemonicCode.INSTANCE.toMnemonic(entropy);
        } catch (MnemonicException.MnemonicLengthException e) {
            // cannot happen
            throw new RuntimeException(e);
        }
        this.seed = MnemonicCode.toSeed(mnemonicCode, passphrase);
        this.creationTimeSeconds = creationTimeSeconds;
    }

    private static byte[] getEntropy(SecureRandom random, int bits) {
        checkArgument(bits <= MAX_SEED_ENTROPY_BITS, "requested entropy size too large");

        byte[] seed = new byte[bits / 8];
        random.nextBytes(seed);
        return seed;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean includePrivate) {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
        if (includePrivate)
            helper.addValue(toHexString()).add("mnemonicCode", getMnemonicString());
        else
            helper.addValue("unencrypted");
        return helper.toString();
    }

    /** Returns the seed as hex or null if encrypted. */
    @Nullable
    public String toHexString() {
        return seed != null ? Hex.encodeHexString(seed) : null;
    }

    @Nullable
    public byte[] getSecretBytes() {
        return getMnemonicAsBytes();
    }

    @Nullable
    public byte[] getSeedBytes() {
        return seed;
    }

    public long getCreationTimeSeconds() {
        return creationTimeSeconds;
    }

    public void setCreationTimeSeconds(long creationTimeSeconds) {
        this.creationTimeSeconds = creationTimeSeconds;
    }

    private byte[] getMnemonicAsBytes() {
        return getMnemonicString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeterministicSeed other = (DeterministicSeed) o;
        return creationTimeSeconds == other.creationTimeSeconds
                && Objects.equals(mnemonicCode, other.mnemonicCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(creationTimeSeconds, mnemonicCode);
    }

    /**
     * Check if our mnemonic is a valid mnemonic phrase for our word list.
     * Does nothing if we are encrypted.
     *
     * @throws MnemonicException if check fails
     */
    public void check() throws MnemonicException {
        if (mnemonicCode != null)
            MnemonicCode.INSTANCE.check(mnemonicCode);
    }

    /** Get the mnemonic code, or null if unknown. */
    @Nullable
    public List<String> getMnemonicCode() {
        return mnemonicCode;
    }

    /** Get the mnemonic code as string, or null if unknown. */
    @Nullable
    public String getMnemonicString() {
        return mnemonicCode != null ? MnemonicCode.SPACE_JOINER.join(mnemonicCode) : null;
    }

    private static List<String> decodeMnemonicCode(byte[] mnemonicCode) {
        return decodeMnemonicCode(new String(mnemonicCode, StandardCharsets.UTF_8));
    }

    private static List<String> decodeMnemonicCode(String mnemonicCode) {
        return Splitter.on(" ").splitToList(mnemonicCode);
    }

}
