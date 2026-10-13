#### New Address Structure

##### Format: Base58 (similar to Bitcoin)）

Example: 4mvr3DNkpWY9ikpGy4maaMSQqUmXjR2hp

##### Address generation process:

1. Perform sha256 and hash160 on the public key to get a 20-byte hash
2. Perform hash160 twice and obtain the first 4 bytes of the result to get the checksum
3. Concatenate the 20-byte hash and the 4-byte checksum to get a 24-byte array
4. Convert the 24-byte array to the Base58 format address

##### Address length: 26-33

The Base58 address length obtained by converting the 24-byte array to Base58 is from 26 to 33 bytes.

##### For detailed implementation of addresses in the xdagj project, please refer to crypto/Base58.java, below are some main implementations:

sha256hash160

```java
public static byte[] sha256hash160(Bytes input) {
    Bytes32 sha256 = sha256(input);
    RIPEMD160Digest digest = new RIPEMD160Digest();
    digest.update(sha256.toArray(), 0, sha256.size());
    byte[] out = new byte[20];
    digest.doFinal(out, 0);
    return out;
}
```

hash160 to Base58

```java
public static String encodeChecked(byte[] payload) {
    byte[] addressBytes = new byte[payload.length + 4];
    System.arraycopy(payload, 0, addressBytes, 0, payload.length);
    byte[] checksum = Hash.hashTwice(payload);
    System.arraycopy(checksum, 0, addressBytes, payload.length, 4);
    return Base58.encode(addressBytes);
}
```

Base58 to hash160

```java
public static byte[] decodeChecked(String input) throws AddressFormatException {
    byte[] decoded  = decode(input);
    if (decoded.length < 4)
        throw new AddressFormatException.InvalidDataLength("Input too short: " + decoded.length);
    byte[] data = Arrays.copyOfRange(decoded, 0, decoded.length - 4);
    byte[] checksum = Arrays.copyOfRange(decoded, decoded.length - 4, decoded.length);
    byte[] actualChecksum = Arrays.copyOfRange(Hash.hashTwice(data), 0, 4);
    if (!Arrays.equals(checksum, actualChecksum))
        throw new AddressFormatException.InvalidChecksum();
    return data;
}
```

check Base58

```java
public static boolean checkAddress(String input) {
    byte[] decoded;
    try {
        decoded  = decode(input);
    } catch (Exception e) {
        return false;
    }
    if (decoded.length < 4) return false;
    byte[] data = Arrays.copyOfRange(decoded, 0, decoded.length - 4);
    byte[] checksum = Arrays.copyOfRange(decoded, decoded.length - 4, decoded.length);
    byte[] actualChecksum = Arrays.copyOfRange(Hash.hashTwice(data), 0, 4);
    return Arrays.equals(checksum, actualChecksum);
}
```

checkout bytes24 (hash160 + checksum)

```java
public static boolean checkBytes24(byte[] data) {
    if (data.length != 24) return false;
    byte[] data20 = Arrays.copyOfRange(data, 0, data.length - 4);
    byte[] checksum = Arrays.copyOfRange(data, data.length - 4, data.length);
    byte[] actualChecksum = Arrays.copyOfRange(Hash.hashTwice(data20), 0, 4);
    return Arrays.equals(checksum, actualChecksum);
}
```

