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

import com.google.common.base.MoreObjects;
import com.google.common.io.BaseEncoding;
import io.xdag.config.Config;
import io.xdag.crypto.bip32.DumpedPrivateKey;
import io.xdag.crypto.bip38.EncryptableItem;
import io.xdag.crypto.bip38.EncryptedData;
import io.xdag.crypto.bip38.KeyCrypter;
import io.xdag.crypto.bip38.KeyCrypterException;
import io.xdag.crypto.jce.ECKeyFactory;
import io.xdag.crypto.jce.ECKeyPairGenerator;
import io.xdag.crypto.jce.ECSignatureFactory;
import io.xdag.crypto.jce.XdagProvider;
import io.xdag.utils.BIUtils;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.FormatDateUtils;
import io.xdag.utils.HashUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.bitcoin.NativeSecp256k1;
import org.bitcoin.NativeSecp256k1Util;
import org.bitcoin.Secp256k1Context;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.util.encoders.Base64;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import static com.google.common.base.Preconditions.*;

@Slf4j
public class ECKey implements EncryptableItem,Serializable {

    public static final BaseEncoding HEX = BaseEncoding.base16().lowerCase();

    /** The parameters of the secp256k1 curve that Ethereum uses. */
    public static final ECDomainParameters CURVE;
    public static final ECParameterSpec CURVE_SPEC;

    /** Sorts oldest keys first, newest last. */
    public static final Comparator<ECKey> AGE_COMPARATOR = new Comparator<ECKey>() {

        @Override
        public int compare(ECKey k1, ECKey k2) {
            if (k1.creationTimeSeconds == k2.creationTimeSeconds)
                return 0;
            else
                return k1.creationTimeSeconds > k2.creationTimeSeconds ? 1 : -1;
        }
    };

    /**
     * Equal to CURVE.getN().shiftRight(1), used for canonicalising the S value of a
     * signature. ECDSA signatures are mutable in the sense that for a given (R, S)
     * pair, then both (R, S) and (R, N - S mod N) are valid signatures. Canonical
     * signatures are those where 1 <= S <= N/2
     *
     * <p>
     * See
     * https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures
     */
    static final BigInteger HALF_CURVE_ORDER;
    static final ECKey DUMMY;
    static final long serialVersionUID = -6632834828111610783L;
    static final SecureRandom secureRandom;
    static final BigInteger SECP256K1N = new BigInteger(
            "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);
    static {
        // All clients must agree on the curve to use by agreement. Ethereum uses
        // secp256k1.
        X9ECParameters params = SECNamedCurves.getByName("secp256k1");
        CURVE = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
        CURVE_SPEC = new ECParameterSpec(params.getCurve(), params.getG(), params.getN(), params.getH());
        HALF_CURVE_ORDER = params.getN().shiftRight(1);
        secureRandom = new SecureRandom();
        DUMMY = fromPrivate(BigInteger.ONE);
    }

//    protected ECPoint pub;

    protected LazyECPoint pubLazy;

    @Nullable
    protected BigInteger privBi;

    // The two parts of the key. If "priv" is set, "pub" can always be calculated.
    // If "pub" is set but
    // not "priv", we
    // can only verify signatures not make them.
    private final PrivateKey privKey;
    // the Java Cryptographic Architecture provider to use for Signature
    // this is set along with the PrivateKey privKey and must be compatible
    // this provider will be used when selecting a Signature instance
    // https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html
    private Provider provider;

    // Creation time of the key in seconds since the epoch, or zero if the key was deserialized from a version that did
    // not have this field.
    @Getter
    protected long creationTimeSeconds;

    protected KeyCrypter keyCrypter;
    protected EncryptedData encryptedPrivateKey;

//    // Transient because it's calculated on demand.
//    private transient byte[] pubKeyHash;

    private byte[] nodeId;

    /**
     * Generates an entirely new keypair.
     *
     * <p>
     * BouncyCastle will be used as the Java Security Provider
     */
    public ECKey() {
        this(secureRandom);
    }

    /**
     * Generate a new keypair using the given Java Security Provider.
     *
     * <p>
     * All private key operations will use the provider.
     */
    public ECKey(Provider provider, SecureRandom secureRandom) {
        this.provider = provider;
        final KeyPairGenerator generator = ECKeyPairGenerator.getInstance(provider, secureRandom);
//        final KeyGenerationParameters keygenParams = new ECKeyGenerationParameters(CURVE, secureRandom);
        final KeyPair keyPair = generator.generateKeyPair();
        this.privKey = keyPair.getPrivate();
        if (this.privKey instanceof BCECPrivateKey) {
            this.privBi = ((BCECPrivateKey) this.privKey).getS();
        } else if (this.privKey instanceof ECPrivateKey) {
            this.privBi = ((ECPrivateKey) this.privKey).getS();
        } else {
            throw new AssertionError(
                    "Expected Provider "
                            + provider.getName()
                            + " to produce a subtype of ECPrivateKey, found "
                            + this.privKey.getClass());
        }
        final PublicKey pubKey = keyPair.getPublic();
        if (pubKey instanceof BCECPublicKey) {
//            pub = ((BCECPublicKey) pubKey).getQ();
            pubLazy = getPointWithCompression(((BCECPublicKey) pubKey).getQ(), true);
        } else if (pubKey instanceof ECPublicKey) {
//            pub = extractPublicKey((ECPublicKey) pubKey);
            pubLazy = getPointWithCompression(extractPublicKey((ECPublicKey) pubKey), true);
        } else {
            throw new AssertionError(
                    "Expected Provider "
                            + provider.getName()
                            + " to produce a subtype of ECPublicKey, found "
                            + pubKey.getClass());
        }

        creationTimeSeconds = FormatDateUtils.currentTimeSeconds();
    }

    /**
     * Generates an entirely new keypair with the given {@link SecureRandom} object.
     *
     * <p>
     * BouncyCastle will be used as the Java Security Provider
     *
     * @param secureRandom
     *            -
     */
    public ECKey(SecureRandom secureRandom) {
        this(XdagProvider.getInstance(), secureRandom);
    }

    /**
     * Pair a private key with a public EC point.
     *
     * <p>
     * All private key operations will use the provider.
     */
    public ECKey(Provider provider, PrivateKey privKey, ECPoint pub) {
        this.provider = provider;

        if (privKey == null || isECPrivateKey(privKey)) {
            this.privKey = privKey;
        } else {
            throw new IllegalArgumentException(
                    "Expected EC private key, given a private key object with class "
                            + privKey.getClass().toString()
                            + " and algorithm "
                            + privKey.getAlgorithm());
        }

        if (pub == null) {
            throw new IllegalArgumentException("Public key may not be null");
        }

        if (pub.isInfinity()) {
            throw new IllegalArgumentException(
                    "Public key must not be a point at infinity, probably your private key is incorrect");
        }

//        this.pub = pub;
        this.pubLazy = getPointWithCompression(checkNotNull(pub), true);
    }

    /**
     * Pair a private key integer with a public EC point
     *
     * <p>
     * BouncyCastle will be used as the Java Security Provider
     */
    public ECKey(BigInteger priv, ECPoint pub) {
        this(XdagProvider.getInstance(), privateKeyFromBigInteger(priv), pub);
    }

    protected ECKey(@Nullable BigInteger priv, ECPoint pub, boolean compressed) {
        this(priv, getPointWithCompression(checkNotNull(pub), compressed));
    }

    protected ECKey(@Nullable BigInteger priv, LazyECPoint pub) {
        if (priv != null) {
            checkArgument(priv.bitLength() <= 32 * 8, "private key exceeds 32 bytes: %s bits", priv.bitLength());
            // Try and catch buggy callers or bad key imports, etc. Zero and one are special because these are often
            // used as sentinel values and because scripting languages have a habit of auto-casting true and false to
            // 1 and 0 or vice-versa. Type confusion bugs could therefore result in private keys with these values.
            checkArgument(!priv.equals(BigInteger.ZERO));
            checkArgument(!priv.equals(BigInteger.ONE));
        }
        this.privBi = priv;
        this.privKey = privateKeyFromBigInteger(this.privBi);
        this.pubLazy = checkNotNull(pub);
    }

    /*
     * Convert a Java JCE ECPublicKey into a BouncyCastle ECPoint
     */
    private static ECPoint extractPublicKey(final ECPublicKey ecPublicKey) {
        final java.security.spec.ECPoint publicPointW = ecPublicKey.getW();
        final BigInteger xCoord = publicPointW.getAffineX();
        final BigInteger yCoord = publicPointW.getAffineY();
        return CURVE.getCurve().createPoint(xCoord, yCoord);
    }

    /*
     * Test if a generic private key is an EC private key
     *
     * it is not sufficient to check that privKey is a subtype of ECPrivateKey as
     * the SunPKCS11 Provider will return a generic PrivateKey instance a fallback
     * that covers this case is to check the key algorithm
     */
    private static boolean isECPrivateKey(PrivateKey privKey) {
        return privKey instanceof ECPrivateKey || "EC".equals(privKey.getAlgorithm());
    }

    /** Convert a BigInteger into a PrivateKey object */
    private static PrivateKey privateKeyFromBigInteger(BigInteger priv) {
        if (priv == null) {
            return null;
        } else {
            try {
                return ECKeyFactory.getInstance(XdagProvider.getInstance())
                        .generatePrivate(new ECPrivateKeySpec(priv, CURVE_SPEC));
            } catch (InvalidKeySpecException ex) {
                throw new AssertionError("Assumed correct key spec statically");
            }
        }
    }

    /**
     * Creates an ECKey given the private key only.
     *
     * @param privKey
     *            -
     * @return -
     */
    public static ECKey fromPrivate(BigInteger privKey) {
        return new ECKey(privKey, CURVE.getG().multiply(privKey));
    }

    /**
     * Creates an ECKey given the private key only.
     *
     * @param privKeyBytes
     *            -
     * @return -
     */
    public static ECKey fromPrivate(byte[] privKeyBytes) {
        return fromPrivate(new BigInteger(1, privKeyBytes));
    }

    /**
     * Creates an ECKey that cannot be used for signing, only verifying signatures,
     * from the given point. The compression state of pub will be preserved.
     *
     * @param pub
     *            -
     * @return -
     */
    public static ECKey fromPublicOnly(ECPoint pub) {
        return new ECKey(null, pub);
    }

    /**
     * Creates an ECKey that cannot be used for signing, only verifying signatures,
     * from the given encoded point. The compression state of pub will be preserved.
     *
     * @param pub
     *            -
     * @return -
     */
    public static ECKey fromPublicOnly(byte[] pub) {
        return new ECKey(null, CURVE.getCurve().decodePoint(pub));
    }

    /**
     * Returns a copy of this key, but with the public point represented in uncompressed form. Normally you would
     * never need this: it's for specialised scenarios or when backwards compatibility in encoded form is necessary.
     */
    public ECKey decompress() {
        if (!pubLazy.isCompressed())
            return this;
        else {
            return new ECKey(getPrivKey(), getPointWithCompression(pubLazy.get(), false));
        }
    }

    /**
     * Returns public key bytes from the given private key. To convert a byte array
     * into a BigInteger, use <tt> new BigInteger(1, bytes);</tt>
     *
     * @param privKey
     *            -
     * @param compressed
     *            -
     * @return -
     */
    public static byte[] publicKeyFromPrivate(BigInteger privKey, boolean compressed) {
        ECPoint point = CURVE.getG().multiply(privKey);
        return point.getEncoded(compressed);
    }

//    /**
//     * Compute an address from an encoded public key.
//     *
//     * @param pubBytes
//     *            an encoded (uncompressed) public key
//     * @return 20-byte address
//     */
//    public static byte[] computeAddress(byte[] pubBytes) {
//        return HashUtils.sha3omit12(Arrays.copyOfRange(pubBytes, 1, pubBytes.length));
//    }

//    /**
//     * Compute an address from a public point.
//     *
//     * @param pubPoint
//     *            a public point
//     * @return 20-byte address
//     */
//    public static byte[] computeAddress(ECPoint pubPoint) {
//        return computeAddress(pubPoint.getEncoded(/* uncompressed */ false));
//    }

    /**
     * Recover the public key from an encoded node id.
     *
     * @param nodeId
     *            a 64-byte X,Y point pair
     */
    public static ECKey fromNodeId(byte[] nodeId) {
        check(nodeId.length == 64, "Expected a 64 byte node id");
        byte[] pubBytes = new byte[65];
        System.arraycopy(nodeId, 0, pubBytes, 1, nodeId.length);
        pubBytes[0] = 0x04; // uncompressed
        return ECKey.fromPublicOnly(pubBytes);
    }

    /**
     * Given a piece of text and a message signature encoded in base64, returns an
     * ECKey containing the public key that was used to sign it. This can then be
     * compared to the expected public key to determine if the signature was
     * correct.
     *
     * @param messageHash
     *            a piece of human readable text that was signed
     * @param signatureBase64
     *            The Ethereum-format message signature in base64
     * @return -
     * @throws SignatureException
     *             If the public key could not be recovered or if there was a
     *             signature format error.
     */
    public static byte[] signatureToKeyBytes(byte[] messageHash, String signatureBase64)
            throws SignatureException {
        byte[] signatureEncoded;
        try {
            signatureEncoded = Base64.decode(signatureBase64);
        } catch (RuntimeException e) {
            // This is what you get back from Bouncy Castle if base64 doesn't decode :(
            throw new SignatureException("Could not decode base64", e);
        }
        // Parse the signature bytes into r/s and the selector value.
        if (signatureEncoded.length < 65) {
            throw new SignatureException(
                    "Signature truncated, expected 65 bytes and got " + signatureEncoded.length);
        }

        return signatureToKeyBytes(
                messageHash,
                ECDSASignature.fromComponents(
                        Arrays.copyOfRange(signatureEncoded, 1, 33),
                        Arrays.copyOfRange(signatureEncoded, 33, 65),
                        (byte) (signatureEncoded[0] & 0xFF)));
    }

    public static byte[] signatureToKeyBytes(byte[] messageHash, ECDSASignature sig)
            throws SignatureException {
        check(messageHash.length == 32, "messageHash argument has length " + messageHash.length);
        int header = sig.v;
        // The header byte: 0x1B = first key with even y, 0x1C = first key with odd y,
        // 0x1D = second key with even y, 0x1E = second key with odd y
        if (header < 27 || header > 34) {
            throw new SignatureException("Header byte out of range: " + header);
        }
        if (header >= 31) {
            header -= 4;
        }
        int recId = header - 27;
        byte[] key = ECKey.recoverPubBytesFromSignature(recId, sig, messageHash);
        if (key == null) {
            throw new SignatureException("Could not recover public key from signature");
        }
        return key;
    }

    /**
     * Compute the key that signed the given signature.
     *
     * @param messageHash
     *            32-byte hash of message
     * @param signatureBase64
     *            Base-64 encoded signature
     * @return ECKey
     */
    public static ECKey signatureToKey(byte[] messageHash, String signatureBase64)
            throws SignatureException {
        final byte[] keyBytes = signatureToKeyBytes(messageHash, signatureBase64);
        return ECKey.fromPublicOnly(keyBytes);
    }

    /**
     * Compute the key that signed the given signature.
     *
     * @param messageHash
     *            32-byte hash of message
     * @param sig
     *            -
     * @return ECKey
     */
    public static ECKey signatureToKey(byte[] messageHash, ECDSASignature sig)
            throws SignatureException {
        final byte[] keyBytes = signatureToKeyBytes(messageHash, sig);
        return ECKey.fromPublicOnly(keyBytes);
    }

    /**
     * Verifies the given ECDSA signature against the message bytes using the public
     * key bytes.
     *
     * <p>
     * When using native ECDSA verification, data must be 32 bytes, and no element
     * may be larger than 520 bytes.
     *
     * @param data
     *            Hash of the data to verify.
     * @param signature
     *            signature.
     * @param pub
     *            The public key bytes to use.
     * @return -
     */
    public static boolean verify(byte[] data, ECDSASignature signature, byte[] pub) {
        if (Secp256k1Context.isEnabled()) {
            try {
                return NativeSecp256k1.verify(data, signature.encodeToDER(), pub);
            } catch (NativeSecp256k1Util.AssertFailException e) {
                log.error("Caught AssertFailException inside secp256k1", e);
                return false;
            }
        }

        ECDSASigner signer = new ECDSASigner();
        ECPublicKeyParameters params = new ECPublicKeyParameters(CURVE.getCurve().decodePoint(pub), CURVE);
        signer.init(false, params);
        try {
            return signer.verifySignature(data, signature.r, signature.s);
        } catch (NullPointerException npe) {
            // Bouncy Castle contains a bug that can cause NPEs given specially crafted
            // signatures.
            // Those signatures are inherently invalid/attack sigs so we just fail them here
            // rather than
            // crash the thread.
            log.error("Caught NPE inside bouncy castle", npe);
            return false;
        }
    }

    /**
     * Verifies the given ASN.1 encoded ECDSA signature against a hash using the
     * public key.
     *
     * @param data
     *            Hash of the data to verify.
     * @param signature
     *            signature.
     * @param pub
     *            The public key bytes to use.
     * @return -
     */
    public static boolean verify(byte[] data, byte[] signature, byte[] pub) {
        if (Secp256k1Context.isEnabled()) {
            try {
                return NativeSecp256k1.verify(data, signature, pub);
            } catch (NativeSecp256k1Util.AssertFailException e) {
                log.error("Caught AssertFailException inside secp256k1", e);
                return false;
            }
        }
        return verify(data, ECDSASignature.decodeFromDER(signature), pub);
    }

    /**
     * Returns true if the given pubkey is canonical, i.e. the correct length taking
     * into account compression.
     *
     * @param pubkey
     *            -
     * @return -
     */
    public static boolean isPubKeyCanonical(byte[] pubkey) {
        if (pubkey[0] == 0x04) {
            // Uncompressed pubkey
            if (pubkey.length != 65) {
                return false;
            }
        } else if (pubkey[0] == 0x02 || pubkey[0] == 0x03) {
            // Compressed pubkey
            if (pubkey.length != 33) {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    /**
     * Given the components of a signature and a selector value, recover and return
     * the public key that generated the signature according to the algorithm in
     * SEC1v2 section 4.1.6.
     *
     * <p>
     * The recId is an index from 0 to 3 which indicates which of the 4 possible
     * keys is the correct one. Because the key recovery operation yields multiple
     * potential keys, the correct key must either be stored alongside the
     * signature, or you must be willing to try each recId in turn until you find
     * one that outputs the key you are expecting.
     *
     * <p>
     * If this method returns null it means recovery was not possible and recId
     * should be iterated.
     *
     * <p>
     * Given the above two points, a correct usage of this method is inside a for
     * loop from 0 to 3, and if the output is null OR a key that is not the one you
     * expect, you try again with the next recId.
     *
     * @param recId
     *            Which possible key to recover.
     * @param sig
     *            the R and S components of the signature, wrapped.
     * @param messageHash
     *            Hash of the data that was signed.
     * @return 65-byte encoded public key
     */
    public static byte[] recoverPubBytesFromSignature(
            int recId, ECDSASignature sig, byte[] messageHash) {
        check(recId >= 0, "recId must be positive");
        check(sig.r.signum() >= 0, "r must be positive");
        check(sig.s.signum() >= 0, "s must be positive");
        check(messageHash != null, "messageHash must not be null");
        // 1.0 For j from 0 to h (h == recId here and the loop is outside this function)
        // 1.1 Let x = r + jn
        BigInteger n = CURVE.getN(); // Curve order.
        BigInteger i = BigInteger.valueOf((long) recId / 2);
        BigInteger x = sig.r.add(i.multiply(n));
        // 1.2. Convert the integer x to an octet string X of length mlen using the
        // conversion routine
        // specified in Section 2.3.7, where mlen = ⌈(log2 p)/8⌉ or mlen = ⌈m/8⌉.
        // 1.3. Convert the octet string (16 set binary digits)||X to an elliptic curve
        // point R using
        // the
        // conversion routine specified in Section 2.3.4. If this conversion routine
        // outputs
        // “invalid”, then
        // do another iteration of Step 1.
        //
        // More concisely, what these points mean is to use X as a compressed public
        // key.
        ECCurve.Fp curve = (ECCurve.Fp) CURVE.getCurve();
        BigInteger prime = curve.getQ(); // Bouncy Castle is not consistent about the letter it uses for the prime.
        if (x.compareTo(prime) >= 0) {
            // Cannot have point co-ordinates larger than this as everything takes place
            // modulo Q.
            return null;
        }
        // Compressed keys require you to know an extra bit of data about the y-coord as
        // there are two
        // possibilities.
        // So it's encoded in the recId.
        ECPoint R = decompressKey(x, (recId & 1) == 1);
        // 1.4. If nR != point at infinity, then do another iteration of Step 1 (callers
        // responsibility).
        if (!R.multiply(n).isInfinity()) {
            return null;
        }
        // 1.5. Compute e from M using Steps 2 and 3 of ECDSA signature verification.
        BigInteger e = new BigInteger(1, messageHash);
        // 1.6. For k from 1 to 2 do the following. (loop is outside this function via
        // iterating
        // recId)
        // 1.6.1. Compute a candidate public key as:
        // Q = mi(r) * (sR - eG)
        //
        // Where mi(x) is the modular multiplicative inverse. We transform this into the
        // following:
        // Q = (mi(r) * s ** R) + (mi(r) * -e ** G)
        // Where -e is the modular additive inverse of e, that is z such that z + e = 0
        // (mod n). In the
        // above equation
        // ** is point multiplication and + is point addition (the EC group operator).
        //
        // We can find the additive inverse by subtracting e from zero then taking the
        // mod. For example
        // the additive
        // inverse of 3 modulo 11 is 8 because 3 + 8 mod 11 = 0, and -3 mod 11 = 8.
        BigInteger eInv = BigInteger.ZERO.subtract(e).mod(n);
        BigInteger rInv = sig.r.modInverse(n);
        BigInteger srInv = rInv.multiply(sig.s).mod(n);
        BigInteger eInvrInv = rInv.multiply(eInv).mod(n);
        ECPoint.Fp q = (ECPoint.Fp) ECAlgorithms.sumOfTwoMultiplies(CURVE.getG(), eInvrInv, R, srInv);
        // result sanity check: point must not be at infinity
        if (q.isInfinity()) {
            return null;
        }
        return q.getEncoded(/* compressed */ false);
    }

    /**
     * Decompress a compressed public key (x co-ord and low-bit of y-coord).
     *
     * @param xBN
     *            -
     * @param yBit
     *            -
     * @return -
     */
    public static ECPoint decompressKey(BigInteger xBN, boolean yBit) {
        X9IntegerConverter x9 = new X9IntegerConverter();
        byte[] compEnc = x9.integerToBytes(xBN, 1 + x9.getByteLength(CURVE.getCurve()));
        compEnc[0] = (byte) (yBit ? 0x03 : 0x02);
        return CURVE.getCurve().decodePoint(compEnc);
    }

    private static void check(boolean test, String message) {
        if (!test) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Returns true if this key doesn't have access to private key bytes. This may
     * be because it was never given any private key bytes to begin with (a watching
     * key).
     *
     * @return -
     */
    public boolean isPubKeyOnly() {
        return privKey == null && privBi == null;
    }

    /**
     * Returns true if this key has access to private key bytes. Does the opposite
     * of {@link #isPubKeyOnly()}.
     *
     * @return -
     */
    public boolean hasPrivKey() {
        return privBi!= null || privKey != null;
    }

    /**
     * Gets the encoded public key value.
     *
     * @return 65-byte encoded public key
     */
    public byte[] getPubKey(boolean compressed) {
        return pubLazy.getEncoded(compressed);
    }

    public byte[] getPubKey() {
        return pubLazy.getEncoded();
    }

    /** Gets the hash160 form of the public key (as seen in addresses). */
    public byte[] getPubKeyHash(boolean compressed) {
        return HashUtils.sha256hash160(this.pubLazy.getEncoded(compressed));
    }

    /** Gets the hash160 form of the public key (as seen in addresses). */
    public byte[] getPubKeyHash() {
        return HashUtils.sha256hash160(this.pubLazy.getEncoded());
    }

    /**
     * Gets the public key in the form of an elliptic curve point object from Bouncy
     * Castle.
     *
     * @return -
     */
    public ECPoint getPubKeyPoint() {
        return pubLazy.get();
    }

    /**
     * Gets the private key in the form of an integer field element. The public key
     * is derived by performing EC point addition this number of times (i.e. point
     * multiplying).
     *
     * @return -
     * @throws java.lang.IllegalStateException
     *             if the private key bytes are not available.
     */
    public BigInteger getPrivKey() {
        if (privKey == null) {
            throw new MissingPrivateKeyException();
        } else if (privKey instanceof BCECPrivateKey) {
            return ((BCECPrivateKey) privKey).getD();
        } else {
            throw new MissingPrivateKeyException();
        }
    }

    /**
     * Returns whether this key is using the compressed form or not. Compressed pubkeys are only 33 bytes, not 64.
     */
    public boolean isCompressed() {
        return pubLazy.isCompressed();
    }

    public String getPrivateKeyAsWiF(Config config) {
        return getPrivateKeyEncoded(config).toString();
    }

    private String toString(boolean includePrivate, @Nullable KeyParameter aesKey, @Nullable Config config) {
        final MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
        helper.add("pub HEX", getPublicKeyAsHex());
        if (includePrivate) {
            ECKey decryptedKey = isEncrypted() ? decrypt(checkNotNull(aesKey)) : this;
            try {
                helper.add("priv HEX", decryptedKey.getPrivateKeyAsHex());
                helper.add("priv WIF", decryptedKey.getPrivateKeyAsWiF(config));
            } catch (IllegalStateException e) {
                // TODO: Make hasPrivKey() work for deterministic keys and fix this.
            } catch (Exception e) {
                final String message = e.getMessage();
                helper.add("priv EXCEPTION", e.getClass().getName() + (message != null ? ": " + message : ""));
            }
        }
        if (creationTimeSeconds > 0)
            helper.add("creationTimeSeconds", creationTimeSeconds);
        helper.add("keyCrypter", keyCrypter);
        if (includePrivate)
            helper.add("encryptedPrivateKey", encryptedPrivateKey);
        helper.add("isEncrypted", isEncrypted());
        helper.add("isPubKeyOnly", isPubKeyOnly());
        return helper.toString();
    }

    @Override
    public String toString() {
        return toString(false, null, null);
    }

    /**
     * Produce a string rendering of the ECKey INCLUDING the private key. Unless you
     * absolutely need the private key it is better for security reasons to just use
     * toString().
     *
     * @return -
     */
    public String toStringWithPrivate() {
        StringBuilder b = new StringBuilder();
        b.append(toString());
        if (privKey != null && privKey instanceof BCECPrivateKey) {
            b.append(" priv:")
                    .append(BytesUtils.toHexString(((BCECPrivateKey) privKey).getD().toByteArray()));
        }
        return b.toString();
    }

    public ECDSASignature sign(Sha256Hash input) throws KeyCrypterException {
        return sign(input, null);
    }

    /**
     * Signs the given hash and returns the R and S components as BigIntegers. In the Bitcoin protocol, they are
     * usually encoded using DER format, so you want {@link ECKey.ECDSASignature#encodeToDER()}
     * instead. However sometimes the independent components can be useful, for instance, if you're doing to do further
     * EC maths on them.
     *
     * @param aesKey The AES key to use for decryption of the private key. If null then no decryption is required.
     * @throws KeyCrypterException if there's something wrong with aesKey.
     * @throws ECKey.MissingPrivateKeyException if this key cannot sign because it's pubkey only.
     */
    public ECDSASignature sign(Sha256Hash input, @Nullable KeyParameter aesKey) throws KeyCrypterException {
        KeyCrypter crypter = getKeyCrypter();
        if (crypter != null) {
            if (aesKey == null)
                throw new KeyIsEncryptedException();
            return decrypt(aesKey).sign(input);
        } else {
            // No decryption of private key required.
            if (privBi == null)
                throw new MissingPrivateKeyException();
        }
        return doSign(input.getBytes(), privBi);
    }

    /**
     * Signs the given hash and returns the R and S components as BigIntegers and
     * put them in ECDSASignature
     *
     * @param input
     *            to sign
     * @return ECDSASignature signature that contains the R and S components
     */
    public ECDSASignature doSign(byte[] input, BigInteger privateKeyForSigning) {
        if (input.length != 32) {
            throw new IllegalArgumentException(
                    "Expected 32 byte input to ECDSA signature, not " + input.length);
        }
        // No decryption of private key required.
        if (privKey == null) {
            throw new MissingPrivateKeyException();
        }
        if (privKey instanceof BCECPrivateKey) {
            ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
            ECPrivateKeyParameters privKeyParams = new ECPrivateKeyParameters(privateKeyForSigning, CURVE);
            signer.init(true, privKeyParams);
            BigInteger[] components = signer.generateSignature(input);
            return new ECDSASignature(components[0], components[1]).toCanonicalised();
        } else {
            try {
                final Signature ecSig = ECSignatureFactory.getRawInstance(provider);
                ecSig.initSign(privKey);
                ecSig.update(input);
                final byte[] derSignature = ecSig.sign();
                return ECDSASignature.decodeFromDER(derSignature).toCanonicalised();
            } catch (SignatureException | InvalidKeyException ex) {
                throw new RuntimeException("ECKey signing error", ex);
            }
        }
    }

    /**
     * Takes the keccak hash (32 bytes) of data and returns the ECDSA signature
     *
     * @param messageHash
     *            -
     * @return -
     * @throws IllegalStateException
     *             if this ECKey does not have the private part.
     */
    public ECDSASignature sign(byte[] messageHash) {
        ECDSASignature sig = doSign(messageHash, getPrivKey());
        // Now we have to work backwards to figure out the recId needed to recover the
        // signature.
        int recId = -1;
        byte[] thisKey = this.pubLazy.getEncoded(/* compressed */ false);
        for (int i = 0; i < 4; i++) {
            byte[] k = ECKey.recoverPubBytesFromSignature(i, sig, messageHash);
            if (k != null && Arrays.equals(k, thisKey)) {
                recId = i;
                break;
            }
        }
        if (recId == -1) {
            throw new RuntimeException(
                    "Could not construct a recoverable key. This should never happen.");
        }
        sig.v = (byte) (recId + 27);
        return sig;
    }

    /**
     * Verifies the given ASN.1 encoded ECDSA signature against a hash using the
     * public key.
     *
     * @param data
     *            Hash of the data to verify.
     * @param signature
     *            signature.
     * @return -
     */
    public boolean verify(byte[] data, byte[] signature) {
        return ECKey.verify(data, signature, getPubKey());
    }

    /**
     * Verifies the given R/S pair (signature) against a hash using the public key.
     *
     * @param sigHash
     *            -
     * @param signature
     *            -
     * @return -
     */
    public boolean verify(byte[] sigHash, ECDSASignature signature) {
        return ECKey.verify(sigHash, signature, getPubKey());
    }

    /**
     * Returns true if this pubkey is canonical, i.e. the correct length taking into
     * account compression.
     *
     * @return -
     */
    public boolean isPubKeyCanonical() {
        return isPubKeyCanonical(pubLazy.getEncoded(/* uncompressed */ false));
    }

    /**
     * Generates the NodeID based on this key, that is the public key without first
     * format byte
     */
    public byte[] getNodeId() {
        if (nodeId == null) {
            byte[] nodeIdWithFormat = getPubKey(false);
            nodeId = new byte[nodeIdWithFormat.length - 1];
            System.arraycopy(nodeIdWithFormat, 1, nodeId, 0, nodeId.length);
        }
        return nodeId;
    }

    /**
     * Returns a 32 byte array containing the private key, or null if the key is
     * encrypted or public only
     *
     * @return -
     */
    public byte[] getPrivKeyBytes() {
        if(privBi != null) {
            return BytesUtils.bigIntegerToBytes(privBi, 32);
        }
        if (privKey != null) {
            if (privKey instanceof BCECPrivateKey) {
                return BytesUtils.bigIntegerToBytes(((BCECPrivateKey) privKey).getD(), 32);
            }
        }
        return BytesUtils.bigIntegerToBytes(getPrivKey(), 32);
    }

    /**
     * Exports the private key in the form used by Bitcoin Core's "dumpprivkey" and "importprivkey" commands. Use
     * the {@link DumpedPrivateKey#toString()} method to get the string.
     *
     * @param config The network this key is intended for use on.
     * @return Private key bytes as a {@link DumpedPrivateKey}.
     * @throws IllegalStateException if the private key is not available.
     */
    public DumpedPrivateKey getPrivateKeyEncoded(Config config) {
        return new DumpedPrivateKey(config, getPrivKeyBytes(), this.pubLazy.isCompressed());
    }

    public DumpedPrivateKey getPrivateKeyEncoded(Config config, boolean isCompressed) {
        return new DumpedPrivateKey(config, getPrivKeyBytes(), isCompressed);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof ECKey)) return false;
        ECKey other = (ECKey) o;
        return Objects.equals(this.privBi, other.privBi)
                && Objects.equals(this.pubLazy, other.pubLazy)
                && Objects.equals(this.creationTimeSeconds, other.creationTimeSeconds);
    }

    @Override
    public int hashCode() {
        return pubLazy.hashCode();
    }

    /**
     * Indicates whether the private key is encrypted (true) or not (false).
     * A private key is deemed to be encrypted when there is both a KeyCrypter and the encryptedPrivateKey is non-zero.
     */
    @Override
    public boolean isEncrypted() {
        return keyCrypter != null && encryptedPrivateKey != null && encryptedPrivateKey.encryptedBytes.length > 0;
    }

    @Nullable
    @Override
    public byte[] getSecretBytes() {
        if (hasPrivKey())
            return getPrivKeyBytes();
        else
            return null;
    }

    @Nullable
    @Override
    public EncryptedData getEncryptedData() {
        return getEncryptedPrivateKey();
    }

    @Nullable
    public KeyCrypter getKeyCrypter() {
        return keyCrypter;
    }

    /**
     * Returns the the encrypted private key bytes and initialisation vector for this ECKey, or null if the ECKey
     * is not encrypted.
     */
    @Nullable
    public EncryptedData getEncryptedPrivateKey() {
        return encryptedPrivateKey;
    }

    /**
     * Groups the two components that make up a signature, and provides a way to
     * encode to Base64 form, which is how ECDSA signatures are represented when
     * embedded in other data structures in the Ethereum protocol. The raw
     * components can be useful for doing further EC maths on them.
     */
    public static class ECDSASignature {
        /** The two components of the signature. */
        public final BigInteger r, s;

        public byte v;

        /**
         * Constructs a signature with the given components. Does NOT automatically
         * canonicalise the signature.
         *
         * @param r
         *            -
         * @param s
         *            -
         */
        public ECDSASignature(BigInteger r, BigInteger s) {
            this.r = r;
            this.s = s;
        }

        private static ECDSASignature fromComponents(byte[] r, byte[] s) {
            return new ECDSASignature(new BigInteger(1, r), new BigInteger(1, s));
        }

        public static ECDSASignature fromComponents(byte[] r, byte[] s, byte v) {
            ECDSASignature signature = fromComponents(r, s);
            signature.v = v;
            return signature;
        }

        public static boolean validateComponents(BigInteger r, BigInteger s, byte v) {

            if (v != 27 && v != 28) {
                return false;
            }

            if (BIUtils.isLessThan(r, BigInteger.ONE)) {
                return false;
            }
            if (BIUtils.isLessThan(s, BigInteger.ONE)) {
                return false;
            }

            if (!BIUtils.isLessThan(r, SECP256K1N)) {
                return false;
            }
            if (!BIUtils.isLessThan(s, SECP256K1N)) {
                return false;
            }

            return true;
        }

        /**
         * DER is an international standard for serializing data structures which is widely used in cryptography.
         * It's somewhat like protocol buffers but less convenient. This method returns a standard DER encoding
         * of the signature, as recognized by OpenSSL and other libraries.
         */
        public byte[] encodeToDER() {
            try {
                return derByteStream().toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);  // Cannot happen.
            }
        }

        public static ECDSASignature decodeFromDER(byte[] bytes) {
            ASN1InputStream decoder = null;
            try {
                decoder = new ASN1InputStream(bytes);
                DLSequence seq = (DLSequence) decoder.readObject();
                if (seq == null) {
                    throw new RuntimeException("Reached past end of ASN.1 stream.");
                }
                ASN1Integer r, s;
                try {
                    r = (ASN1Integer) seq.getObjectAt(0);
                    s = (ASN1Integer) seq.getObjectAt(1);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException(e);
                }
                // OpenSSL deviates from the DER spec by interpreting these values as unsigned,
                // though they
                // should not be
                // Thus, we always use the positive versions. See:
                // http://r6.ca/blog/20111119T211504Z.html
                return new ECDSASignature(r.getPositiveValue(), s.getPositiveValue());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (decoder != null) {
                    try {
                        decoder.close();
                    } catch (IOException x) {
                    }
                }
            }
        }

        protected ByteArrayOutputStream derByteStream() throws IOException {
            // Usually 70-72 bytes.
            ByteArrayOutputStream bos = new ByteArrayOutputStream(72);
            DERSequenceGenerator seq = new DERSequenceGenerator(bos);
            seq.addObject(new ASN1Integer(r));
            seq.addObject(new ASN1Integer(s));
            seq.close();
            return bos;
        }

        public boolean validateComponents() {
            return validateComponents(r, s, v);
        }

        /**
         * Will automatically adjust the S component to be less than or equal to half
         * the curve order, if necessary. This is required because for every signature
         * (r,s) the signature (r, -s (mod N)) is a valid signature of the same message.
         * However, we dislike the ability to modify the bits of a xdag transaction
         * after it's been signed, as that violates various assumed invariants. Thus in
         * future only one of those forms will be considered legal and the other will be
         * banned.
         *
         * @return -
         */
        public ECDSASignature toCanonicalised() {
            if (s.compareTo(HALF_CURVE_ORDER) > 0) {
                // The order of the curve is the number of valid points that exist on that
                // curve. If S is in
                // the upper
                // half of the number of valid points, then bring it back to the lower half.
                // Otherwise,
                // imagine that
                // N = 10
                // s = 8, so (-8 % 10 == 2) thus both (r, 8) and (r, 2) are valid solutions.
                // 10 - 8 == 2, giving us always the latter solution, which is canonical.
                return new ECDSASignature(r, CURVE.getN().subtract(s));
            } else {
                return this;
            }
        }

        /** @return - */
        public String toBase64() {
            byte[] sigData = new byte[65]; // 1 header + 32 bytes for R + 32 bytes for S
            sigData[0] = v;
            System.arraycopy(BytesUtils.bigIntegerToBytes(this.r, 32), 0, sigData, 1, 32);
            System.arraycopy(BytesUtils.bigIntegerToBytes(this.s, 32), 0, sigData, 33, 32);
            return new String(Base64.encode(sigData), StandardCharsets.UTF_8);
        }

        public byte[] toByteArray() {
            final byte fixedV = this.v >= 27 ? (byte) (this.v - 27) : this.v;

            return BytesUtils.merge(
                    BytesUtils.bigIntegerToBytes(this.r, 32),
                    BytesUtils.bigIntegerToBytes(this.s, 32),
                    new byte[] { fixedV });
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ECDSASignature signature = (ECDSASignature) o;

            if (!r.equals(signature.r)) {
                return false;
            }
            if (!s.equals(signature.s)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = r.hashCode();
            result = 31 * result + s.hashCode();
            return result;
        }
    }

    /**
     * Returns true if the given pubkey is in its compressed form.
     */
    public static boolean isPubKeyCompressed(byte[] encoded) {
        if (encoded.length == 33 && (encoded[0] == 0x02 || encoded[0] == 0x03))
            return true;
        else if (encoded.length == 65 && encoded[0] == 0x04)
            return false;
        else
            throw new IllegalArgumentException(Hex.encodeHexString(encoded));
    }

    public String getPrivateKeyAsHex() {
        return HEX.encode(getPrivKeyBytes());
    }

    public String getPublicKeyAsHex() {
        return HEX.encode(pubLazy.getEncoded());
    }

    /** Returns true if this key is watch only, meaning it has a public key but no private key. */
    public boolean isWatching() {
        return isPubKeyOnly() && !isEncrypted();
    }

//    /**
//     * Indicates whether the private key is encrypted (true) or not (false).
//     * A private key is deemed to be encrypted when there is both a KeyCrypter and the encryptedPrivateKey is non-zero.
//     */
//    @Override
//    public boolean isEncrypted() {
//        return keyCrypter != null && encryptedPrivateKey != null && encryptedPrivateKey.encryptedBytes.length > 0;
//    }

    /**
     * Sets the creation time of this key. Zero is a convention to mean "unavailable". This method can be useful when
     * you have a raw key you are importing from somewhere else.
     */
    public void setCreationTimeSeconds(long newCreationTimeSeconds) {
        if (newCreationTimeSeconds < 0)
            throw new IllegalArgumentException("Cannot set creation time to negative value: " + newCreationTimeSeconds);
        creationTimeSeconds = newCreationTimeSeconds;
    }

    /**
     * Constructs a key that has an encrypted private component. The given object wraps encrypted bytes and an
     * initialization vector. Note that the key will not be decrypted during this call: the returned ECKey is
     * unusable for signing unless a decryption key is supplied.
     */
    public static ECKey fromEncrypted(EncryptedData encryptedPrivateKey, KeyCrypter crypter, byte[] pubKey) {
        ECKey key = fromPublicOnly(pubKey);
        key.encryptedPrivateKey = checkNotNull(encryptedPrivateKey);
        key.keyCrypter = checkNotNull(crypter);
        return key;
    }

    /**
     * Create an encrypted private key with the keyCrypter and the AES key supplied.
     * This method returns a new encrypted key and leaves the original unchanged.
     *
     * @param keyCrypter The keyCrypter that specifies exactly how the encrypted bytes are created.
     * @param aesKey The KeyParameter with the AES encryption key (usually constructed with keyCrypter#deriveKey and cached as it is slow to create).
     * @return encryptedKey
     */
    public ECKey encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) throws KeyCrypterException {
        checkNotNull(keyCrypter);
        final byte[] privKeyBytes = getPrivKeyBytes();
        EncryptedData encryptedPrivateKey = keyCrypter.encrypt(privKeyBytes, aesKey);
        ECKey result = ECKey.fromEncrypted(encryptedPrivateKey, keyCrypter, getPubKey());
        result.setCreationTimeSeconds(creationTimeSeconds);
        return result;
    }

    /**
     * Create a decrypted private key with the keyCrypter and AES key supplied. Note that if the aesKey is wrong, this
     * has some chance of throwing KeyCrypterException due to the corrupted padding that will result, but it can also
     * just yield a garbage key.
     *
     * @param keyCrypter The keyCrypter that specifies exactly how the decrypted bytes are created.
     * @param aesKey The KeyParameter with the AES encryption key (usually constructed with keyCrypter#deriveKey and cached).
     */
    public ECKey decrypt(KeyCrypter keyCrypter, KeyParameter aesKey) throws KeyCrypterException {
        checkNotNull(keyCrypter);
        // Check that the keyCrypter matches the one used to encrypt the keys, if set.
        if (this.keyCrypter != null && !this.keyCrypter.equals(keyCrypter))
            throw new KeyCrypterException("The keyCrypter being used to decrypt the key is different to the one that was used to encrypt it");
        checkState(encryptedPrivateKey != null, "This key is not encrypted");
        byte[] unencryptedPrivateKey = keyCrypter.decrypt(encryptedPrivateKey, aesKey);
        if (unencryptedPrivateKey.length != 32)
            throw new KeyCrypterException.InvalidCipherText(
                    "Decrypted key must be 32 bytes long, but is " + unencryptedPrivateKey.length);
        ECKey key = ECKey.fromPrivate(unencryptedPrivateKey, pubLazy.isCompressed());
        if (!Arrays.equals(key.getPubKey(), getPubKey()))
            throw new KeyCrypterException("Provided AES key is wrong");
        key.setCreationTimeSeconds(creationTimeSeconds);
        return key;
    }

    /**
     * Create a decrypted private key with AES key. Note that if the AES key is wrong, this
     * has some chance of throwing KeyCrypterException due to the corrupted padding that will result, but it can also
     * just yield a garbage key.
     *
     * @param aesKey The KeyParameter with the AES encryption key (usually constructed with keyCrypter#deriveKey and cached).
     */
    public ECKey decrypt(KeyParameter aesKey) throws KeyCrypterException {
        final KeyCrypter crypter = getKeyCrypter();
        if (crypter == null)
            throw new KeyCrypterException("No key crypter available");
        return decrypt(crypter, aesKey);
    }

    /**
     * Creates decrypted private key if needed.
     */
    public ECKey maybeDecrypt(@Nullable KeyParameter aesKey) throws KeyCrypterException {
        return isEncrypted() && aesKey != null ? decrypt(aesKey) : this;
    }

    public static boolean encryptionIsReversible(ECKey originalKey, ECKey encryptedKey, KeyCrypter keyCrypter, KeyParameter aesKey) {
        try {
            ECKey rebornUnencryptedKey = encryptedKey.decrypt(keyCrypter, aesKey);
            byte[] originalPrivateKeyBytes = originalKey.getPrivKeyBytes();
            byte[] rebornKeyBytes = rebornUnencryptedKey.getPrivKeyBytes();
            if (!Arrays.equals(originalPrivateKeyBytes, rebornKeyBytes)) {
                log.error("The check that encryption could be reversed failed for {}", originalKey);
                return false;
            }
            return true;
        } catch (KeyCrypterException kce) {
            log.error(kce.getMessage());
            return false;
        }
    }


    /**
     * Creates an ECKey given the private key only. The public key is calculated from it (this is slow).
     * @param compressed Determines whether the resulting ECKey will use a compressed encoding for the public key.
     */
    public static ECKey fromPrivate(BigInteger privKey, boolean compressed) {
        ECPoint point = publicPointFromPrivate(privKey);
        return new ECKey(privKey, getPointWithCompression(point, compressed));
    }

    /**
     * Creates an ECKey given the private key only. The public key is calculated from it (this is slow).
     * @param compressed Determines whether the resulting ECKey will use a compressed encoding for the public key.
     */
    public static ECKey fromPrivate(byte[] privKeyBytes, boolean compressed) {
        return fromPrivate(new BigInteger(1, privKeyBytes), compressed);
    }

    private static LazyECPoint getPointWithCompression(ECPoint point, boolean compressed) {
        return new LazyECPoint(point, compressed);
    }

    /**
     * Returns public key point from the given private key. To convert a byte array into a BigInteger,
     * use {@code new BigInteger(1, bytes);}
     */
    public static ECPoint publicPointFromPrivate(BigInteger privKey) {
        /*
         * TODO: FixedPointCombMultiplier currently doesn't support scalars longer than the group order,
         * but that could change in future versions.
         */
        if (privKey.bitLength() > CURVE.getN().bitLength()) {
            privKey = privKey.mod(CURVE.getN());
        }
        return new FixedPointCombMultiplier().multiply(CURVE.getG(), privKey);
    }

    /**
     * Utility for compressing an elliptic curve point. Returns the same point if it's already compressed.
     * See the ECKey class docs for a discussion of point compression.
     */
    public static LazyECPoint compressPoint(LazyECPoint point) {
        return point.isCompressed() ? point : getPointWithCompression(point.get(), true);
    }

    @SuppressWarnings("serial")
    public static class MissingPrivateKeyException extends RuntimeException {
    }

    public static class KeyIsEncryptedException extends MissingPrivateKeyException {
    }
}
