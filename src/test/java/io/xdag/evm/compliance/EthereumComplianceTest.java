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
package io.xdag.evm.compliance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.xdag.evm.DataWord;
import io.xdag.evm.EVM;
import io.xdag.evm.LogInfo;
import io.xdag.evm.chainspec.BaseSpec;
import io.xdag.evm.chainspec.PrecompiledContracts;
import io.xdag.evm.chainspec.Spec;
import io.xdag.evm.client.BlockStore;
import io.xdag.evm.client.BlockStoreMock;
import io.xdag.evm.client.Repository;
import io.xdag.evm.client.RepositoryMock;
import io.xdag.evm.compliance.spec.Account;
import io.xdag.evm.compliance.spec.Environment;
import io.xdag.evm.compliance.spec.Exec;
import io.xdag.evm.compliance.spec.TestCase;
import io.xdag.evm.program.Program;
import io.xdag.evm.program.ProgramResult;
import io.xdag.evm.program.invoke.ProgramInvoke;
import io.xdag.evm.program.invoke.ProgramInvokeImpl;
import io.xdag.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import org.junit.Test;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@Slf4j
public class EthereumComplianceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void runAllTests() throws IOException {

        TypeReference<HashMap<String, TestCase>> typeRef = new TypeReference<>() {};

        File rootTestDirectory = new File("src/test/resources/VMTests");
        List<File> files = Files.walk(rootTestDirectory.toPath())
                .map(p -> p.toFile())
                .filter(f -> f.getName().endsWith(".json")
                        && !f.getAbsolutePath().contains("vmPerformance"))
                .collect(Collectors.toList());

        for (File file : files) {
            HashMap<String, TestCase> suite = objectMapper.readValue(file, typeRef);
            for (Entry<String, TestCase> entry : suite.entrySet()) {
                runTest(file.getName(), entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * run a VM test
     *
     * @param testName
     * @param testCase
     */
    private void runTest(String fileName, String testName, TestCase testCase) {
        log.info("Running test: file = {}, test = {}", fileName, testName);

        Exec exec = testCase.getExec();
        Bytes code = Bytes.fromHexString(exec.getCode());
        DataWord address = DataWord.of(exec.getAddress());
        DataWord origin = DataWord.of(exec.getOrigin());
        DataWord caller = DataWord.of(exec.getCaller());
        long gas = DataWord.of(exec.getGas()).longValue();
        DataWord gasPrice = DataWord.of(exec.getGasPrice());
        DataWord value = DataWord.of(exec.getValue());
        Bytes data = Bytes.fromHexString(exec.getData());

        Environment env = testCase.getEnvironment();
        DataWord prevHash = DataWord.ZERO;
        DataWord coinbase = DataWord.of(env.getCurrentCoinbase());
        DataWord timestamp = DataWord.of(env.getCurrentTimestamp());
        DataWord number = DataWord.of(env.getCurrentNumber());
        DataWord difficulty = DataWord.of(env.getCurrentDifficulty());
        DataWord gasLimit = DataWord.of(env.getCurrentGasLimit());

        RepositoryMock mock = new RepositoryMock();
        for (Entry<String, Account> entry : testCase.getPre().entrySet()) {
            Bytes ad = Bytes.fromHexString(entry.getKey());
            Account ac = entry.getValue();
            mock.createAccount(ad);
            mock.addBalance(ad, DataWord.of(ac.getBalance()).value());
            mock.saveCode(ad, Bytes.fromHexString(ac.getCode()));
            mock.setNonce(ad, DataWord.of(ac.getNonce()).intValue());
            for (Entry<String, String> row : ac.getStorage().entrySet()) {
                mock.putStorageRow(ad, DataWord.of(row.getKey()), DataWord.of(row.getValue()));
            }
        }
        Repository repository = mock;
        Repository originalRepository = mock.clone();
        BlockStore blockStore = new BlockStoreMock();
        int callDepth = 0;
        boolean isStaticCall = false;

        Spec spec = new BaseSpec() {
            @Override
            public PrecompiledContracts getPrecompiledContracts() {
                return address -> null;
            }
        };
        EVM vm = new EVM(spec);
        ProgramInvoke programInvoke = new ProgramInvokeImpl(address, origin, caller, gas, gasPrice, value, data,
                prevHash, coinbase, timestamp, number, difficulty, gasLimit, repository, originalRepository, blockStore,
                callDepth, isStaticCall);
        Program program = new Program(code, programInvoke, spec);

        vm.play(program);

        ProgramResult result = program.getResult();
        if (testCase.getGas() != null) {
            log.debug("Checking gas usage ..");
            assertEquals(gas - DataWord.of(testCase.getGas()).longValue(), result.getGasUsed());
        }
        if (testCase.getLogs() != null) {
            log.debug("Checking logs ..");
            assertEquals(testCase.getLogs(), getLogsHash(result.getLogs()));
        }
        if (testCase.getOut() != null) {
            log.debug("Checking return data ..");
            assertEquals(testCase.getOut(), result.getReturnData().toHexString());
        }
        if (testCase.getPost() != null) {
            log.debug("Checking account state ..");
            for (Entry<String, Account> entry : testCase.getPost().entrySet()) {
                Bytes ad = Bytes.fromHexString(entry.getKey());
                Account ac = entry.getValue();
                assertEquals(DataWord.of(ac.getBalance()).value(), repository.getBalance(ad));
                assertEquals(ac.getCode(), repository.getCode(ad).toHexString());
                assertEquals(DataWord.of(ac.getNonce()).longValue(), repository.getNonce(ad));
                for (Entry<String, String> row : ac.getStorage().entrySet()) {
                    assertEquals(DataWord.of(row.getValue()), repository.getStorageRow(ad, DataWord.of(row.getKey())));
                }
            }
        }
    }

    private String getLogsHash(List<LogInfo> logs) {
        List<RlpType> list = new ArrayList<>();
        for (LogInfo log : logs) {
            RlpString address = RlpString.create(log.getAddress().toArray());
            List<RlpType> topics = log.getTopics().stream()
                    .map(t -> RlpString.create(t.getData().toArray()))
                    .collect(Collectors.toList());
            RlpString data = RlpString.create(log.getData().toArray());
            list.add(new RlpList(address, new RlpList(topics), data));
        }
        byte[] encoded = RlpEncoder.encode(new RlpList(list));
        return BytesUtils.toHexStringWith0x(Hash.keccak256(encoded));
    }
}

