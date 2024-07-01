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
package io.xdag.consensus;


import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.xdag.Wallet;
import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.core.XAmount;
import io.xdag.core.XdagField;
import io.xdag.crypto.SampleKeys;
import io.xdag.crypto.Sign;
import io.xdag.pool.PoolAwardManagerImpl;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.XdagRandomUtils;
import io.xdag.utils.XdagSha256Digest;
import io.xdag.utils.XdagTime;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.hyperledger.besu.crypto.KeyPair;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static io.xdag.config.Constants.MIN_GAS;
import static io.xdag.core.XUnit.XDAG;
import static io.xdag.pool.PoolAwardManagerImpl.BlockRewardHistorySender.awardMessageHistoryQueue;
import static io.xdag.utils.BasicUtils.*;
import static io.xdag.utils.BytesUtils.compareTo;
import static org.junit.Assert.*;

public class TaskTest {
    private Wallet wallet;

    @Before
    public void setUp() {
        String pwd = "password";
        Config config = new DevnetConfig();
        wallet = new Wallet(config);
        wallet.unlock(pwd);
        KeyPair key = KeyPair.create(SampleKeys.SRIVATE_KEY, Sign.CURVE, Sign.CURVE_NAME);
        wallet.setAccounts(Collections.singletonList(key));
        wallet.flush();
        wallet.lock();
    }

    @After
    public void tearDown() throws IOException {
        wallet.delete();
        awardMessageHistoryQueue.clear();
    }

    @Test
    public void testTaskClone() throws CloneNotSupportedException {
        Task t = new Task();

        XdagField[] xfArray = new XdagField[2];
        xfArray[0] = new XdagField(MutableBytes.EMPTY);
        xfArray[0].setSum(1);
        xfArray[0].setType(XdagField.FieldType.XDAG_FIELD_HEAD);

        xfArray[1] = new XdagField(MutableBytes.EMPTY);
        xfArray[1].setSum(2);
        xfArray[1].setType(XdagField.FieldType.XDAG_FIELD_IN);

        t.setTask(xfArray);
        t.setDigest(new XdagSha256Digest());
        t.setTaskTime(System.currentTimeMillis());
        t.setTaskIndex(1);

        Task cloneTask = (Task) t.clone();

        assertNotEquals(t, cloneTask);
        assertNotEquals(t.getTask(), cloneTask.getTask());
        assertNotEquals(t.getDigest(), cloneTask.getDigest());
        assertNotEquals(t.getTask()[0], cloneTask.getTask()[0]);
        assertNotEquals(t.getTask()[1], cloneTask.getTask()[1]);

        assertEquals(t.getTaskTime(), cloneTask.getTaskTime());
        assertEquals(t.getTaskIndex(), cloneTask.getTaskIndex());
        assertEquals(t.getTask()[0].getSum(), cloneTask.getTask()[0].getSum());
        assertEquals(t.getTask()[0].getType(), cloneTask.getTask()[0].getType());
        assertEquals(t.getTask()[1].getSum(), cloneTask.getTask()[1].getSum());
        assertEquals(t.getTask()[1].getType(), cloneTask.getTask()[1].getType());
    }

    @Test
    public void testTaskConvertToJsonFormatTask() {
        // task
        // send to pool
        Task newTask = new Task();
        XdagField[] task = new XdagField[2];
        MutableBytes preHash = MutableBytes.wrap(XdagRandomUtils.nextNewBytes(32));
        MutableBytes taskSeed = MutableBytes.wrap(XdagRandomUtils.nextNewBytes(32));
        task[0] = new XdagField(preHash);
        task[0].setSum(1);
        task[0].setType(XdagField.FieldType.XDAG_FIELD_HEAD);
        task[1] = new XdagField(taskSeed);
        task[1].setSum(2);
        task[1].setType(XdagField.FieldType.XDAG_FIELD_IN);
        newTask.setTask(task);
        long currentTime = System.currentTimeMillis();
        newTask.setTaskTime(currentTime);
        newTask.setTaskIndex(1);
        // Task  json information
        String shareInfo = newTask.toJsonString();
        JsonElement element = JsonParser.parseString(shareInfo);
        assertTrue(element.isJsonObject());
        JSONObject jsonObject = new JSONObject(shareInfo);
        assertEquals(jsonObject.getJSONObject("msgContent").getJSONObject("task").getString("preHash"),
                preHash.toUnprefixedHexString());
        assertEquals(jsonObject.getJSONObject("msgContent").getJSONObject("task").getString("taskSeed"),
                taskSeed.toUnprefixedHexString());
        assertEquals(jsonObject.getJSONObject("msgContent").get("taskTime"), currentTime);
        assertEquals(jsonObject.getJSONObject("msgContent").get("taskIndex"), 1);
        assertTrue(jsonObject.getJSONObject("msgContent").isNull("digest"));
        assertEquals(1, jsonObject.getInt("msgType"));
    }

    @Test
    public void testSend2PoolsTxInfoConvertToJsonFormat() {
        // example:
        // {
        //   "txBlock":"7aec7d2c57b79bb08339d0875fccc8f88466bb8c9192ca2d19470d6530704da9",
        //   "preHash":"9a5627d68ac1f9b2be6bb59e403786b90ffa1221abf8a1fbce376d92f19951ad",
        //   "share":"e6cfaab9a59ba187f0a45db0b169c21bb48f09b32787085b99633ab027c1b49b",
        //   "amount":60.700000000,
        //   "fee":0.100000000,
        //   "donateBlock":"7aec7d2c57b79bb08339d0875fccc8f88466bb8c9192ca2d19470d6530704da9",
        //   "donate":3.200000000
        // }
        double fundRation = 5;

        PoolAwardManagerImpl.TransactionInfoSender transactionInfoSender = new PoolAwardManagerImpl.TransactionInfoSender();
        Bytes32 preHash = Bytes32.wrap(Bytes.random(32));
        Bytes32 txHash = Bytes32.wrap(Bytes.random(32));
        transactionInfoSender.setPreHash(preHash);
        transactionInfoSender.setTxBlock(txHash);
        transactionInfoSender.setDonateBlock(txHash);

        Bytes randomBytes = Bytes.random(12);
        wallet.unlock("password");
        transactionInfoSender.setShare(Bytes32.wrap(BytesUtils.merge(
                hash2byte(keyPair2Hash(wallet.getDefKey())
                ), randomBytes.toArray())));
        transactionInfoSender.setFee(MIN_GAS.toDecimal(9, XDAG).toPlainString());
        XAmount amount = XAmount.of(64, XDAG);
        XAmount fundAmount = amount.multiply(div(fundRation, 100, 6));
        transactionInfoSender.setAmount(amount.subtract(MIN_GAS).subtract(fundAmount).toDecimal(9,
                XDAG).toPlainString());
        transactionInfoSender.setDonate(fundAmount.toDecimal(9, XDAG).toPlainString());
        JsonElement element = JsonParser.parseString(transactionInfoSender.toJsonString());
        assertTrue(element.isJsonObject());
        JSONObject jsonObject = new JSONObject(transactionInfoSender.toJsonString());
        assertEquals(jsonObject.get("txBlock"), txHash.toUnprefixedHexString());
        assertEquals(jsonObject.get("preHash"), preHash.toUnprefixedHexString());
        assertEquals(jsonObject.get("share"), Bytes32.wrap(BytesUtils.merge(
                hash2byte(keyPair2Hash(wallet.getDefKey())
                ), randomBytes.toArray())).toUnprefixedHexString());
        assertEquals(jsonObject.get("amount").toString(), amount.subtract(MIN_GAS).subtract(fundAmount).toDecimal(9,
                XDAG).toPlainString());
        assertEquals(jsonObject.get("fee").toString(), MIN_GAS.toDecimal(9, XDAG).toPlainString());
        assertEquals(jsonObject.get("donateBlock").toString(), txHash.toUnprefixedHexString());
        assertEquals(jsonObject.get("donate").toString(), fundAmount.toDecimal(9, XDAG).toPlainString());
    }

    @Test
    public void testIndexValidity() {
        // 16 blocks per reward cycle
        int awardEpoch = 0xf;
        long mainBlockTime = XdagTime.getMainTime();
        int startIndex = (int) mainBlockTime >> 16 & awardEpoch;
        // 65536 (2^16) is the xdag time interval for generating the main block
        for (int i = 0; i < 10000000; ) {
            assertEquals((((mainBlockTime + 65536L * i)) >> 16 & awardEpoch), startIndex);
            i += 16;
        }
    }

    @Test
    public void testSaveRewardDistributionMessageHistory() throws Exception {
        // Cache the last 16 blocks reward messages
        // send to pool
        double fundRation = 5;
        PoolAwardManagerImpl.TransactionInfoSender transactionInfoSender = new PoolAwardManagerImpl.TransactionInfoSender();
        transactionInfoSender.setFee(MIN_GAS.toDecimal(9, XDAG).toPlainString());
        XAmount amount = XAmount.of(64, XDAG);
        XAmount fundAmount = amount.multiply(div(fundRation, 100, 6));
        transactionInfoSender.setAmount(amount.subtract(MIN_GAS).subtract(fundAmount).toDecimal(9, XDAG).toPlainString());
        transactionInfoSender.setDonate(fundAmount.toDecimal(9, XDAG).toPlainString());
        for (int i = 0; i < 16; i++) {
            Bytes32 preHash = Bytes32.wrap(XdagRandomUtils.nextNewBytes(32));
            Bytes32 txBlock = Bytes32.wrap(XdagRandomUtils.nextNewBytes(32));
            Bytes32 share = Bytes32.wrap(XdagRandomUtils.nextNewBytes(32));
            transactionInfoSender.setShare(share);
            transactionInfoSender.setTxBlock(txBlock);
            transactionInfoSender.setPreHash(preHash);
            transactionInfoSender.setDonateBlock(txBlock);
            awardMessageHistoryQueue.put(transactionInfoSender.toJsonString());
        }
        assertEquals(0, awardMessageHistoryQueue.remainingCapacity());
        assertFalse(awardMessageHistoryQueue.offer(transactionInfoSender.toJsonString()));
        JsonElement element = JsonParser.parseString(PoolAwardManagerImpl.BlockRewardHistorySender.toJsonString());
        assertTrue(element.isJsonObject());
        JSONObject jsonObject = new JSONObject(PoolAwardManagerImpl.BlockRewardHistorySender.toJsonString());
        assertEquals(3, jsonObject.getInt("msgType"));
    }

    @Test
    public void testShare() {
        Bytes32 share1 = Bytes32.wrap(Bytes.fromHexString(
                "0x46a2a0fe035c413d92be9c79a11cfc3695780f65b2f8615ee6ead812a57a4eb1"));
        Bytes32 share2 = Bytes32.wrap(Bytes.fromHexString(
                "46a2a0fe035c413d92be9c79a11cfc3695780f65b2f8615ee6ead812a57a4eb1"));
        assertEquals(0, compareTo(share1.toArray(), 0, 32, share2.toArray(), 0, 32));
    }

    @Test
    public void testShareFromPool() {
        String shareInfo = "{\"msgType\":2," +
                "\"msgContent\":{\"share\":\"d5e79bae5fe5c7d7b7b8d4f4404c517b46fb1f7400000011a215bcbc071e0400\"," +
                "\"hash\":\"f9ab3eb63317e36ae0c0eec512d47001b392e0330f46472ad9da6cf03b546f92\",\"taskIndex\":15}}\n";
        JSONObject shareJson = new JSONObject(shareInfo);
        assertEquals(shareJson.getInt("msgType"), 2);
        assertEquals(shareJson.getJSONObject("msgContent").getString("share"),
                "d5e79bae5fe5c7d7b7b8d4f4404c517b46fb1f7400000011a215bcbc071e0400");
        assertEquals(shareJson.getJSONObject("msgContent").getLong("taskIndex"), 15);

    }
}
