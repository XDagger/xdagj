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
package io.xdag.evm;

import static io.xdag.evm.OpCode.CALL;
import static io.xdag.evm.OpCode.PUSH1;
import static io.xdag.evm.OpCode.REVERT;
import static io.xdag.utils.EVMUtils.getSizeInWords;

import io.xdag.crypto.Hash;
import io.xdag.evm.chainspec.Spec;
import io.xdag.evm.program.Program;
import io.xdag.evm.program.Stack;
import io.xdag.evm.program.exception.ExceptionFactory;
import io.xdag.evm.program.exception.ReturnDataCopyIllegalBoundsException;
import io.xdag.evm.program.exception.StaticCallModificationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class EVM {
    private static final BigInteger THIRTY_TWO = BigInteger.valueOf(32);

    // theoretical limit, used to reduce expensive BigInt arithmetic
    private static final BigInteger MAX_MEM_SIZE = BigInteger.valueOf(Integer.MAX_VALUE);

    private final Spec spec;

    public EVM() {
        this(Spec.DEFAULT);
    }

    public EVM(Spec spec) {
        this.spec = spec;
    }

    private long calcMemGas(FeeSchedule feeSchedule, long oldMemSize, BigInteger newMemSize, long copySize) {
        long gasCost = 0;

        // avoid overflows
        if (newMemSize.compareTo(MAX_MEM_SIZE) > 0) {
            throw ExceptionFactory.gasOverflow(newMemSize, MAX_MEM_SIZE);
        }

        // memory gas calc
        long memoryUsage = (newMemSize.longValue() + 31) / 32 * 32;
        if (memoryUsage > oldMemSize) {
            long memWords = (memoryUsage / 32);
            long memWordsOld = (oldMemSize / 32);
            long memGas = (feeSchedule.getMEMORY() * memWords + memWords * memWords / 512)
                    - (feeSchedule.getMEMORY() * memWordsOld + memWordsOld * memWordsOld / 512);
            gasCost += memGas;
        }

        if (copySize > 0) {
            long copyGas = feeSchedule.getCOPY_GAS() * ((copySize + 31) / 32);
            gasCost += copyGas;
        }
        return gasCost;
    }

    private boolean isDeadAccount(Program program, Bytes addr) {
        // TODO: check EVM specification
        return false;
    }

    public void step(Program program) {
        try {
            OpCode op = OpCode.code(program.getCurrentOp());
            if (op == null) {
                throw ExceptionFactory.invalidOpCode(program.getCurrentOp());
            }

            // validate opcode
            switch (op) {
                case SHL:
                case SHR:
                case SAR:
                    if (!spec.eip145()) {
                        throw ExceptionFactory.invalidOpCode(program.getCurrentOp());
                    }
                    break;
                case EXTCODEHASH:
                    if (!spec.eip1052()) {
                        throw ExceptionFactory.invalidOpCode(program.getCurrentOp());
                    }
                    break;
                case CREATE2:
                    if (!spec.eip1014()) {
                        throw ExceptionFactory.invalidOpCode(program.getCurrentOp());
                    }
                default:
                    break;
            }

            program.verifyStackUnderflow(op.require());
            program.verifyStackOverflow(op.require(), op.ret()); // Check not exceeding stack limits

            long oldMemSize = program.getMemSize();
            Stack stack = program.getStack();

            long gasCost = op.getTier().asInt();
            FeeSchedule feeSchedule = spec.getFeeSchedule();
            long adjustedCallGas = 0;

            // Calculate fees and spend gas
            switch (op) {
                case STOP:
                    gasCost = feeSchedule.getSTOP();
                    break;
                case SUICIDE:
                    gasCost = feeSchedule.getSUICIDE();
                    DataWord suicideAddressWord = stack.get(stack.size() - 1);
                    if (isDeadAccount(program, suicideAddressWord.getLast20Bytes()) &&
                            !program.getBalance(program.getOwnerAddress()).isZero()) {
                        gasCost += feeSchedule.getNEW_ACCT_SUICIDE();
                    }
                    break;
                case SSTORE:
                    DataWord currentValue = program.getCurrentStorageValue(stack.peek());
                    if (currentValue == null)
                        currentValue = DataWord.ZERO;
                    DataWord newValue = stack.get(stack.size() - 2);

                    if (spec.eip1283()) { // Net gas metering for SSTORE
                        if (newValue.equals(currentValue)) {
                            gasCost = feeSchedule.getREUSE_SSTORE();
                        } else {
                            DataWord origValue = program.getOriginalStorageValue(stack.peek());
                            if (origValue == null)
                                origValue = DataWord.ZERO;
                            if (currentValue.equals(origValue)) {
                                if (origValue.isZero()) {
                                    gasCost = feeSchedule.getSET_SSTORE();
                                } else {
                                    gasCost = feeSchedule.getCLEAR_SSTORE();
                                    if (newValue.isZero()) {
                                        program.futureRefundGas(feeSchedule.getREFUND_SSTORE());
                                    }
                                }
                            } else {
                                gasCost = feeSchedule.getREUSE_SSTORE();
                                if (!origValue.isZero()) {
                                    if (currentValue.isZero()) {
                                        program.futureRefundGas(-feeSchedule.getREFUND_SSTORE());
                                    } else if (newValue.isZero()) {
                                        program.futureRefundGas(feeSchedule.getREFUND_SSTORE());
                                    }
                                }
                                if (origValue.equals(newValue)) {
                                    if (origValue.isZero()) {
                                        program.futureRefundGas(
                                                feeSchedule.getSET_SSTORE() - feeSchedule.getREUSE_SSTORE());
                                    } else {
                                        program.futureRefundGas(
                                                feeSchedule.getCLEAR_SSTORE() - feeSchedule.getREUSE_SSTORE());
                                    }
                                }
                            }
                        }
                    } else { // Before EIP-1283 cost calculation
                        if (currentValue.isZero() && !newValue.isZero())
                            gasCost = feeSchedule.getSET_SSTORE();
                        else if (!currentValue.isZero() && newValue.isZero()) {
                            // refund step cost policy.
                            program.futureRefundGas(feeSchedule.getREFUND_SSTORE());
                            gasCost = feeSchedule.getCLEAR_SSTORE();
                        } else {
                            gasCost = feeSchedule.getRESET_SSTORE();
                        }
                    }

                    break;
                case SLOAD:
                    gasCost = feeSchedule.getSLOAD();
                    break;
                case BALANCE:
                    gasCost = feeSchedule.getBALANCE();
                    break;

                // These all operate on memory and therefore potentially expand it:
                case MSTORE:
                case MLOAD:
                    gasCost += calcMemGas(feeSchedule, oldMemSize, memNeeded(stack.peek(), DataWord.of(32)), 0);
                    break;
                case MSTORE8:
                    gasCost += calcMemGas(feeSchedule, oldMemSize, memNeeded(stack.peek(), DataWord.of(1)), 0);
                    break;
                case RETURN:
                case REVERT:
                    gasCost = feeSchedule.getSTOP() + calcMemGas(feeSchedule, oldMemSize,
                            memNeeded(stack.peek(), stack.get(stack.size() - 2)), 0);
                    break;
                case SHA3:
                    gasCost = feeSchedule.getSHA3() + calcMemGas(feeSchedule, oldMemSize,
                            memNeeded(stack.peek(), stack.get(stack.size() - 2)), 0);
                    DataWord size = stack.get(stack.size() - 2);
                    long chunkUsed = getSizeInWords(size.longValueSafe());
                    gasCost += chunkUsed * feeSchedule.getSHA3_WORD();
                    break;
                case CALLDATACOPY:
                case RETURNDATACOPY:
                case CODECOPY:
                    gasCost += calcMemGas(feeSchedule, oldMemSize,
                            memNeeded(stack.peek(), stack.get(stack.size() - 3)),
                            stack.get(stack.size() - 3).longValueSafe());
                    break;
                case EXTCODESIZE:
                    gasCost = feeSchedule.getEXT_CODE_SIZE();
                    break;
                case EXTCODECOPY:
                    gasCost = feeSchedule.getEXT_CODE_COPY() + calcMemGas(feeSchedule, oldMemSize,
                            memNeeded(stack.get(stack.size() - 2), stack.get(stack.size() - 4)),
                            stack.get(stack.size() - 4).longValueSafe());
                    break;
                case EXTCODEHASH:
                    gasCost = feeSchedule.getEXT_CODE_HASH();
                    break;
                case CALL:
                case CALLCODE:
                case DELEGATECALL:
                case STATICCALL:

                    gasCost = feeSchedule.getCALL();
                    DataWord callGasWord = stack.get(stack.size() - 1);

                    DataWord callAddressWord = stack.get(stack.size() - 2);

                    DataWord value = op.callHasValue() ? stack.get(stack.size() - 3) : DataWord.ZERO;

                    // check to see if account does not exist and is not a precompiled contract
                    if (op == CALL) {
                        if (isDeadAccount(program, callAddressWord.getLast20Bytes()) && !value.isZero()) {
                            gasCost += feeSchedule.getNEW_ACCT_CALL();
                        }
                    }

                    // TODO: Make sure this is converted to BigInteger (256num support)
                    if (!value.isZero())
                        gasCost += feeSchedule.getVT_CALL();

                    int opOff = op.callHasValue() ? 4 : 3;
                    BigInteger in = memNeeded(stack.get(stack.size() - opOff),
                            stack.get(stack.size() - opOff - 1)); // in offset+size
                    BigInteger out = memNeeded(stack.get(stack.size() - opOff - 2),
                            stack.get(stack.size() - opOff - 3)); // out offset+size
                    gasCost += calcMemGas(feeSchedule, oldMemSize, in.max(out), 0);

                    if (gasCost > program.getGasLeft()) {
                        throw ExceptionFactory.notEnoughOpGas(op, gasCost, program.getGasLeft());
                    }

                    long available = program.getGasLeft();
                    adjustedCallGas = spec.getCallGas(op, callGasWord.longValueSafe(), available - gasCost);
                    gasCost += adjustedCallGas;
                    break;
                case CREATE:
                    gasCost = feeSchedule.getCREATE() + calcMemGas(feeSchedule, oldMemSize,
                            memNeeded(stack.get(stack.size() - 2), stack.get(stack.size() - 3)), 0);
                    break;
                case CREATE2:
                    DataWord codeSize = stack.get(stack.size() - 3);
                    gasCost = feeSchedule.getCREATE() +
                            calcMemGas(feeSchedule, oldMemSize, memNeeded(stack.get(stack.size() - 2), codeSize), 0) +
                            getSizeInWords(codeSize.longValueSafe()) * feeSchedule.getSHA3_WORD();
                    break;
                case LOG0:
                case LOG1:
                case LOG2:
                case LOG3:
                case LOG4:
                    int nTopics = op.val() - OpCode.LOG0.val();

                    BigInteger dataSize = stack.get(stack.size() - 2).value();
                    BigInteger dataCost = dataSize.multiply(BigInteger.valueOf(feeSchedule.getLOG_DATA_GAS()));
                    if (BigInteger.valueOf(program.getGasLeft()).compareTo(dataCost) < 0) {
                        throw ExceptionFactory.notEnoughOpGas(op, dataCost.longValue(), program.getGasLeft());
                    }

                    gasCost = feeSchedule.getLOG_GAS() +
                            feeSchedule.getLOG_TOPIC_GAS() * nTopics +
                            feeSchedule.getLOG_DATA_GAS() * stack.get(stack.size() - 2).longValue() +
                            calcMemGas(feeSchedule, oldMemSize, memNeeded(stack.peek(), stack.get(stack.size() - 2)), 0);
                    break;
                case EXP:

                    DataWord exp = stack.get(stack.size() - 2);
                    int bytesOccupied = exp.bytesOccupied();
                    gasCost = feeSchedule.getEXP_GAS() + feeSchedule.getEXP_BYTE_GAS() * bytesOccupied;
                    break;
                default:
                    break;
            }

            program.spendGas(gasCost, op.name());

            // Execute operation
            switch (op) {

                case STOP: {
                    program.setHReturn(Bytes.EMPTY);
                    program.stop();
                }
                break;
                case ADD: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.add(word2);
                    program.stackPush(result);
                    program.step();

                }
                break;
                case MUL: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.mul(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case SUB: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.sub(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case DIV: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.div(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case SDIV: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.sDiv(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case MOD: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.mod(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case SMOD: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.sMod(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case EXP: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.exp(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case SIGNEXTEND: {
                    DataWord word1 = program.stackPop();
                    BigInteger k = word1.value();

                    if (k.compareTo(THIRTY_TWO) < 0) {
                        DataWord word2 = program.stackPop();
                        DataWord result = word2.signExtend(k.byteValue());
                        program.stackPush(result);
                    }
                    program.step();
                }
                break;
                case NOT: {
                    DataWord word1 = program.stackPop();
                    DataWord result = word1.bnot();

                    program.stackPush(result);
                    program.step();
                }
                break;
                case LT: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = (word1.value().compareTo(word2.value()) < 0) ? DataWord.ONE : DataWord.ZERO;
                    program.stackPush(result);
                    program.step();
                }
                break;
                case SLT: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = (word1.sValue().compareTo(word2.sValue()) < 0) ? DataWord.ONE : DataWord.ZERO;
                    program.stackPush(result);
                    program.step();
                }
                break;
                case SGT: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = (word1.sValue().compareTo(word2.sValue()) > 0) ? DataWord.ONE : DataWord.ZERO;
                    program.stackPush(result);
                    program.step();
                }
                break;
                case GT: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = (word1.value().compareTo(word2.value()) > 0) ? DataWord.ONE : DataWord.ZERO;
                    program.stackPush(result);
                    program.step();
                }
                break;
                case EQ: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.equals(word2) ? DataWord.ONE : DataWord.ZERO;
                    program.stackPush(result);
                    program.step();
                }
                break;
                case ISZERO: {
                    DataWord word1 = program.stackPop();
                    DataWord result = word1.isZero() ? DataWord.ONE : DataWord.ZERO;
                    program.stackPush(result);
                    program.step();
                }
                break;

                case AND: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.and(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case OR: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.or(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case XOR: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();

                    DataWord result = word1.xor(word2);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case BYTE: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();
                    final DataWord result;
                    if (word1.value().compareTo(THIRTY_TWO) < 0) {
                        byte tmp = word2.getByte(word1.intValue());
                        result = DataWord.of(tmp);
                    } else {
                        result = DataWord.ZERO;
                    }

                    program.stackPush(result);
                    program.step();
                }
                break;
                case SHL: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();
                    DataWord result = word2.shiftLeft(word1);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case SHR: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();
                    DataWord result = word2.shiftRight(word1);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case SAR: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();
                    DataWord result = word2.shiftRightSigned(word1);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case ADDMOD: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();
                    DataWord word3 = program.stackPop();
                    DataWord result = word1.addmod(word2, word3);
                    program.stackPush(result);
                    program.step();
                }
                break;
                case MULMOD: {
                    DataWord word1 = program.stackPop();
                    DataWord word2 = program.stackPop();
                    DataWord word3 = program.stackPop();
                    DataWord result = word1.mulmod(word2, word3);
                    program.stackPush(result);
                    program.step();
                }
                break;

                case SHA3: {
                    DataWord memOffsetData = program.stackPop();
                    DataWord lengthData = program.stackPop();
                    byte[] buffer = program.memoryChunk(memOffsetData.intValueSafe(), lengthData.intValueSafe());
//                    byte[] encoded = Hash.keccak256(buffer);
                    Bytes encoded = Bytes.wrap(Hash.keccak256(buffer));
                    DataWord word = DataWord.of(encoded);

                    program.stackPush(word);
                    program.step();
                }
                break;

                case ADDRESS: {
                    DataWord address = program.getOwnerAddress();

                    program.stackPush(address);
                    program.step();
                }
                break;
                case BALANCE: {
                    DataWord address = program.stackPop();
                    DataWord balance = program.getBalance(address);

                    program.stackPush(balance);
                    program.step();
                }
                break;
                case ORIGIN: {
                    DataWord originAddress = program.getOriginAddress();

                    program.stackPush(originAddress);
                    program.step();
                }
                break;
                case CALLER: {
                    DataWord callerAddress = program.getCallerAddress();

                    program.stackPush(callerAddress);
                    program.step();
                }
                break;
                case CALLVALUE: {
                    DataWord callValue = program.getCallValue();

                    program.stackPush(callValue);
                    program.step();
                }
                break;
                case CALLDATALOAD: {
                    DataWord dataOffs = program.stackPop();
                    DataWord value = program.getDataValue(dataOffs);

                    program.stackPush(value);
                    program.step();
                }
                break;
                case CALLDATASIZE: {
                    DataWord dataSize = program.getDataSize();

                    program.stackPush(dataSize);
                    program.step();
                }
                break;
                case CALLDATACOPY: {
                    DataWord memOffsetData = program.stackPop();
                    DataWord dataOffsetData = program.stackPop();
                    DataWord lengthData = program.stackPop();

                    Bytes msgData = program.getDataCopy(dataOffsetData, lengthData);

                    program.memorySave(memOffsetData.intValueSafe(), lengthData.intValueSafe(), msgData);
                    program.step();
                }
                break;
                case RETURNDATASIZE: {
                    DataWord dataSize = program.getReturnDataBufferSize();

                    program.stackPush(dataSize);
                    program.step();
                }
                break;
                case RETURNDATACOPY: {
                    DataWord memOffsetData = program.stackPop();
                    DataWord dataOffsetData = program.stackPop();
                    DataWord lengthData = program.stackPop();

                    byte[] msgData = program.getReturnDataBufferData(dataOffsetData, lengthData);

                    if (msgData == null) {
                        throw new ReturnDataCopyIllegalBoundsException(dataOffsetData, lengthData,
                                program.getReturnDataBufferSize().longValueSafe());
                    }

                    program.memorySave(memOffsetData.intValueSafe(), lengthData.intValueSafe(), Bytes.wrap(msgData));
                    program.step();
                }
                break;
                case CODESIZE:
                case EXTCODESIZE: {

                    int length;
                    if (op == OpCode.CODESIZE)
                        length = program.getCode().size();
                    else {
                        DataWord address = program.stackPop();
                        length = program.getCodeAt(address).size();
                    }
                    DataWord codeLength = DataWord.of(length);

                    program.stackPush(codeLength);
                    program.step();
                }
                break;
                case CODECOPY:
                case EXTCODECOPY: {

                    Bytes fullCode = Bytes.EMPTY;
                    if (op == OpCode.CODECOPY)
                        fullCode = program.getCode();

                    if (op == OpCode.EXTCODECOPY) {
                        DataWord address = program.stackPop();
                        fullCode = program.getCodeAt(address);
                    }

                    int memOffset = program.stackPop().intValueSafe();
                    int codeOffset = program.stackPop().intValueSafe();
                    int lengthData = program.stackPop().intValueSafe();

                    int sizeToBeCopied = (long) codeOffset + lengthData > fullCode.size()
                            ? (fullCode.size() < codeOffset ? 0 : fullCode.size() - codeOffset)
                            : lengthData;

//                    byte[] codeCopy = new byte[lengthData];
                    MutableBytes codeCopy = MutableBytes.create(lengthData);

                    if (codeOffset < fullCode.size()) {
//                        System.arraycopy(fullCode, codeOffset, codeCopy, 0, sizeToBeCopied);
                        codeCopy.set(0, fullCode.slice(codeOffset, sizeToBeCopied));
                    }

                    program.memorySave(memOffset, lengthData, codeCopy);
                    program.step();
                }
                break;
                case EXTCODEHASH: {
                    DataWord address = program.stackPop();

                    // NOTE: The EXTCODEHASH of an precompiled contract is either c5d246... or 0
                    Bytes code = program.getCodeAt(address);
                    code = (code == null) ? Bytes.EMPTY : code;

                    Bytes codeHash = Bytes.wrap(Hash.keccak256(code.toArray()));
                    program.stackPush(codeHash);
                    program.step();
                }
                break;
                case GASPRICE: {
                    DataWord gasPrice = program.getGasPrice();

                    program.stackPush(gasPrice);
                    program.step();
                }
                break;

                case BLOCKHASH: {

                    int blockIndex = program.stackPop().intValueSafe();

                    DataWord blockHash = program.getBlockHash(blockIndex);

                    program.stackPush(blockHash);
                    program.step();
                }
                break;
                case COINBASE: {
                    DataWord coinbase = program.getBlockCoinbase();

                    program.stackPush(coinbase);
                    program.step();
                }
                break;
                case TIMESTAMP: {
                    DataWord timestamp = program.getBlockTimestamp();

                    program.stackPush(timestamp);
                    program.step();
                }
                break;
                case NUMBER: {
                    DataWord number = program.getBlockNumber();

                    program.stackPush(number);
                    program.step();
                }
                break;
                case DIFFICULTY: {
                    DataWord difficulty = program.getBlockDifficulty();

                    program.stackPush(difficulty);
                    program.step();
                }
                break;
                case GASLIMIT: {
                    DataWord gaslimit = program.getBlockGasLimit();

                    program.stackPush(gaslimit);
                    program.step();
                }
                break;
                case POP: {
                    program.stackPop();
                    program.step();
                }
                break;
                case DUP1:
                case DUP2:
                case DUP3:
                case DUP4:
                case DUP5:
                case DUP6:
                case DUP7:
                case DUP8:
                case DUP9:
                case DUP10:
                case DUP11:
                case DUP12:
                case DUP13:
                case DUP14:
                case DUP15:
                case DUP16: {
                    int n = op.val() - OpCode.DUP1.val() + 1;
                    program.stackPush(stack.get(stack.size() - n)); // same object ref
                    program.step();

                }
                break;
                case SWAP1:
                case SWAP2:
                case SWAP3:
                case SWAP4:
                case SWAP5:
                case SWAP6:
                case SWAP7:
                case SWAP8:
                case SWAP9:
                case SWAP10:
                case SWAP11:
                case SWAP12:
                case SWAP13:
                case SWAP14:
                case SWAP15:
                case SWAP16: {

                    int n = op.val() - OpCode.SWAP1.val() + 2;
                    stack.swap(stack.size() - 1, stack.size() - n);
                    program.step();
                }
                break;
                case LOG0:
                case LOG1:
                case LOG2:
                case LOG3:
                case LOG4: {

                    if (program.isStaticCall())
                        throw new StaticCallModificationException();
                    DataWord address = program.getOwnerAddress();

                    DataWord memStart = stack.pop();
                    DataWord memOffset = stack.pop();

                    int nTopics = op.val() - OpCode.LOG0.val();

                    List<DataWord> topics = new ArrayList<>();
                    for (int i = 0; i < nTopics; ++i) {
                        DataWord topic = stack.pop();
                        topics.add(topic);
                    }

                    Bytes data = Bytes.wrap(program.memoryChunk(memStart.intValueSafe(), memOffset.intValueSafe()));

                    LogInfo logInfo = new LogInfo(address.getLast20Bytes(), topics, data);

                    program.getResult().addLogInfo(logInfo);
                    program.step();
                }
                break;
                case MLOAD: {
                    DataWord addr = program.stackPop();
                    DataWord data = program.memoryLoad(addr);

                    program.stackPush(data);
                    program.step();
                }
                break;
                case MSTORE: {
                    DataWord addr = program.stackPop();
                    DataWord value = program.stackPop();

                    program.memorySave(addr, value);
                    program.step();
                }
                break;
                case MSTORE8: {
                    DataWord addr = program.stackPop();
                    DataWord value = program.stackPop();
                    byte[] byteVal = { value.getByte(31) };
                    program.memorySave(addr.intValueSafe(), byteVal);
                    program.step();
                }
                break;
                case SLOAD: {
                    DataWord key = program.stackPop();
                    DataWord val = program.getCurrentStorageValue(key);

                    if (val == null) {
                        val = DataWord.ZERO;
                    }

                    program.stackPush(val);
                    program.step();
                }
                break;
                case SSTORE: {
                    if (program.isStaticCall())
                        throw new StaticCallModificationException();

                    DataWord addr = program.stackPop();
                    DataWord value = program.stackPop();

                    program.storageSave(addr, value);
                    program.step();
                }
                break;
                case JUMP: {
                    DataWord pos = program.stackPop();
                    int nextPC = program.verifyJumpDest(pos);

                    program.setPC(nextPC);
                }
                break;
                case JUMPI: {
                    DataWord pos = program.stackPop();
                    DataWord cond = program.stackPop();

                    if (!cond.isZero()) {
                        int nextPC = program.verifyJumpDest(pos);

                        program.setPC(nextPC);
                    } else {
                        program.step();
                    }

                }
                break;
                case PC: {
                    int pc = program.getPC();
                    DataWord pcWord = DataWord.of(pc);

                    program.stackPush(pcWord);
                    program.step();
                }
                break;
                case MSIZE: {
                    int memSize = program.getMemSize();
                    DataWord wordMemSize = DataWord.of(memSize);

                    program.stackPush(wordMemSize);
                    program.step();
                }
                break;
                case GAS: {
                    long gasLeft = program.getGasLeft();

                    program.stackPush(DataWord.of(gasLeft));
                    program.step();
                }
                break;

                case PUSH1:
                case PUSH2:
                case PUSH3:
                case PUSH4:
                case PUSH5:
                case PUSH6:
                case PUSH7:
                case PUSH8:
                case PUSH9:
                case PUSH10:
                case PUSH11:
                case PUSH12:
                case PUSH13:
                case PUSH14:
                case PUSH15:
                case PUSH16:
                case PUSH17:
                case PUSH18:
                case PUSH19:
                case PUSH20:
                case PUSH21:
                case PUSH22:
                case PUSH23:
                case PUSH24:
                case PUSH25:
                case PUSH26:
                case PUSH27:
                case PUSH28:
                case PUSH29:
                case PUSH30:
                case PUSH31:
                case PUSH32: {
                    program.step();
                    int nPush = op.val() - PUSH1.val() + 1;

                    byte[] data = program.sweep(nPush);

                    program.stackPush(Bytes.wrap(data));
                }
                break;
                case JUMPDEST: {
                    program.step();
                }
                break;
                case CREATE: {
                    if (program.isStaticCall())
                        throw new StaticCallModificationException();

                    DataWord value = program.stackPop();
                    DataWord inOffset = program.stackPop();
                    DataWord inSize = program.stackPop();
                    long gas = spec.getCreateGas(program.getGasLeft());

                    program.createContract(value, inOffset, inSize, gas);

                    program.step();
                }
                break;
                case CREATE2: {
                    if (program.isStaticCall())
                        throw new StaticCallModificationException();

                    DataWord value = program.stackPop();
                    DataWord inOffset = program.stackPop();
                    DataWord inSize = program.stackPop();
                    DataWord salt = program.stackPop();
                    long gas = spec.getCreateGas(program.getGasLeft());

                    program.createContract2(value, inOffset, inSize, salt, gas);

                    program.step();
                }
                break;
                case CALL:
                case CALLCODE:
                case DELEGATECALL:
                case STATICCALL: {
                    program.stackPop(); // use adjustedCallGas instead of requested
                    DataWord codeAddress = program.stackPop();
                    DataWord value = op.callHasValue() ? program.stackPop() : DataWord.ZERO;

                    if (program.isStaticCall() && op == CALL && !value.isZero())
                        throw new StaticCallModificationException();

                    if (!value.isZero()) {
                        adjustedCallGas += feeSchedule.getSTIPEND_CALL();
                    }

                    DataWord inDataOffs = program.stackPop();
                    DataWord inDataSize = program.stackPop();

                    DataWord outDataOffs = program.stackPop();
                    DataWord outDataSize = program.stackPop();

                    program.memoryExpand(outDataOffs, outDataSize);

                    program.callContract(op, adjustedCallGas, codeAddress, value, inDataOffs, inDataSize, outDataOffs,
                            outDataSize);

                    program.step();
                }
                break;
                case RETURN:
                case REVERT: {
                    DataWord offset = program.stackPop();
                    DataWord size = program.stackPop();

                    Bytes hReturn = Bytes.wrap(program.memoryChunk(offset.intValueSafe(), size.intValueSafe()));
                    program.setHReturn(hReturn);

                    program.step();
                    program.stop();

                    if (op == REVERT) {
                        program.setRevert(true);
                    }
                }
                break;
                case SUICIDE: {
                    if (program.isStaticCall())
                        throw new StaticCallModificationException();

                    DataWord address = program.stackPop();
                    program.suicide(address);

                    program.stop();
                }
                break;
                default:
                    break;
            }

        } catch (RuntimeException e) {
            program.spendAllGas();
            program.resetFutureRefund();
            program.stop();
            throw e;
        }
    }

    public void play(Program program) {
        try {
            while (!program.isStopped()) {
                this.step(program);
            }

        } catch (RuntimeException e) {
            program.setException(e);
        } catch (StackOverflowError soe) {
            log.error("\n !!! StackOverflowError: update your java run command with -Xss2M !!!\n", soe);
            System.exit(-1);
        }
    }

    /**
     * Utility to calculate new total memory size needed for an operation. <br/>
     * Basically just offset + size, unless size is 0, in which case the result is
     * also 0.
     *
     * @param offset
     *            starting position of the memory
     * @param size
     *            number of bytes needed
     * @return offset + size, unless size is 0. In that case memNeeded is also 0.
     */
    private static BigInteger memNeeded(DataWord offset, DataWord size) {
        return size.isZero() ? BigInteger.ZERO : offset.value().add(size.value());
    }
}
