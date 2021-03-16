package io.xdag.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.xdag.crypto.ECKey;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class WalletTest {

    @Test
    public void testDecryptScrypt() throws Exception {
        WalletFile walletFile = load();
        ECKey Key = Wallet.decrypt(PASSWORD, walletFile);
        assertEquals(WalletUtils.toHexStringNoPrefix(Key.getPrivKeyBytes()), (SECRET));
    }

    @Test
    public void testGenerateRandomBytes() {
        assertArrayEquals(Wallet.generateRandomBytes(0), (new byte[] {}));
        assertEquals(Wallet.generateRandomBytes(10).length, (10));
    }

    private WalletFile load() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(WalletTest.SCRYPT, WalletFile.class);
    }

    private static final String PASSWORD = "123456";
    private static final String SECRET = "f868df8c38488468a1d936e53a2711db5ae3fdce0e62cf6c44ea02ee725fa69b";

    private static final String SCRYPT =
            """
                    {
                        "crypto" : {
                            "cipher" : "aes-128-ctr",
                            "cipherparams" : {
                                "iv" : "506cf750331ee03821bc63ddf8029f98"
                            },
                            "ciphertext" : "716e6251120813daeb5ac30b32ba21cd0a216933e689ab2d28aa6a5b06c52ed1",
                            "kdf" : "scrypt",
                            "kdfparams" : {
                                "n" : 2,
                                "p" : 2,
                                "r" : 8,
                                "dklen" : 32,
                                "salt" : "9dbeda842cb063fe3f29517fdacb85dab02fb71e60d3d836b4b5e941c5994ef1"
                            },
                            "mac" : "d69278176cb016b0d79b2ac59122a7847d89dc7480e5b87e80c85d3d963f67cd"
                        },
                        "id" : "3030cad6-8ac7-49b0-903c-6a1c41aa8e79",
                        "version" : 2
                    }""";



}
