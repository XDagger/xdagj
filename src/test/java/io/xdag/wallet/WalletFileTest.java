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
package io.xdag.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.xdag.wallet.WalletFile;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class WalletFileTest {

    @Test
    public void equalsAndHashCodeTest() throws IOException {

        final String AES_128_CTR =
                "{\n"
                        + "    \"crypto\" : {\n"
                        + "        \"cipher\" : \"aes-128-ctr\",\n"
                        + "        \"cipherparams\" : {\n"
                        + "            \"iv\" : \"02ebc768684e5576900376114625ee6f\"\n"
                        + "        },\n"
                        + "        \"ciphertext\" : \"7ad5c9dd2c95f34a92ebb86740b92103a5d1cc4c2eabf3b9a59e1f83f3181216\",\n"
                        + "        \"kdf\" : \"pbkdf2\",\n"
                        + "        \"kdfparams\" : {\n"
                        + "            \"c\" : 262144,\n"
                        + "            \"dklen\" : 32,\n"
                        + "            \"prf\" : \"hmac-sha256\",\n"
                        + "            \"salt\" : \"0e4cf3893b25bb81efaae565728b5b7cde6a84e224cbf9aed3d69a31c981b702\"\n"
                        + "        },\n"
                        + "        \"mac\" : \"2b29e4641ec17f4dc8b86fc8592090b50109b372529c30b001d4d96249edaf62\"\n"
                        + "    },\n"
                        + "    \"id\" : \"af0451b4-6020-4ef0-91ec-794a5a965b01\",\n"
                        + "    \"version\" : 3\n"
                        + "}";
        ObjectMapper objectMapper = new ObjectMapper();
        WalletFile walletFile1 = objectMapper.readValue(AES_128_CTR, WalletFile.class);

        WalletFile.Crypto crypto = new WalletFile.Crypto();
        crypto.setCipher("aes-128-ctr");
        crypto.setCiphertext("7ad5c9dd2c95f34a92ebb86740b92103a5d1cc4c2eabf3b9a59e1f83f3181216");
        crypto.setKdf("pbkdf2");
        crypto.setMac("2b29e4641ec17f4dc8b86fc8592090b50109b372529c30b001d4d96249edaf62");

        WalletFile.CipherParams cipherParams = new WalletFile.CipherParams();
        cipherParams.setIv("02ebc768684e5576900376114625ee6f");
        crypto.setCipherparams(cipherParams);

        WalletFile.Aes128CtrKdfParams kdfParams = new WalletFile.Aes128CtrKdfParams();
        kdfParams.setC(262144);
        kdfParams.setDklen(32);
        kdfParams.setPrf("hmac-sha256");
        kdfParams.setSalt("0e4cf3893b25bb81efaae565728b5b7cde6a84e224cbf9aed3d69a31c981b702");
        crypto.setKdfparams(kdfParams);

        WalletFile walletFile2 = new WalletFile();
        walletFile2.setCrypto(crypto);
        walletFile2.setVersion(3);
        walletFile2.setId("af0451b4-6020-4ef0-91ec-794a5a965b01");

        assertEquals(walletFile1, walletFile2);
        assertEquals(walletFile1.hashCode(), walletFile2.hashCode());
    }
}
