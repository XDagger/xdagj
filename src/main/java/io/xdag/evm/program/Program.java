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
package io.xdag.evm.program;

import static io.xdag.utils.BytesUtils.EMPTY_BYTE_ARRAY;
import static io.xdag.utils.Numeric.isNotCovers;
import static io.xdag.utils.EVMUtils.transfer;
import static io.xdag.utils.BytesUtils.nullToEmpty;

import java.math.BigInteger;
import java.util.Arrays;

import io.xdag.utils.HashUtils;
import io.xdag.evm.DataWord;
import io.xdag.evm.EVM;
import io.xdag.evm.MessageCall;
import io.xdag.evm.OpCode;
import io.xdag.evm.chainspec.PrecompiledContract;
import io.xdag.evm.chainspec.PrecompiledContractContext;
import io.xdag.evm.chainspec.Spec;
import io.xdag.evm.client.Repository;
import io.xdag.evm.program.exception.BytecodeExecutionException;
import io.xdag.evm.program.exception.CallTooDeepException;
import io.xdag.evm.program.exception.ExceptionFactory;
import io.xdag.evm.program.exception.InsufficientBalanceException;
import io.xdag.evm.program.exception.OutOfGasException;
import io.xdag.evm.program.exception.PrecompiledFailureException;
import io.xdag.evm.program.exception.StackUnderflowException;
import io.xdag.evm.program.invoke.ProgramInvoke;
import io.xdag.evm.program.invoke.ProgramInvokeFactory;
import io.xdag.evm.program.invoke.ProgramInvokeFactoryImpl;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;

@Slf4j
public class Program {

    /**
     * This attribute defines the number of recursive calls allowed in the EVM Note:
     * For the JVM to reach this level without a StackOverflow exception, ethereumj
     * may need to be started with a JVM argument to increase the stack size. For
     * example: -Xss10m
     */
    private static final int MAX_DEPTH = 1024;

    // Max size for stack checks
    private static final int MAX_STACKSIZE = 1024;

    private final ProgramInvokeFactory programInvokeFactory = new ProgramInvokeFactoryImpl();
    private final ProgramInvoke invoke;
    private final ProgramResult result;

    private final Spec spec;
    private ProgramPreprocess preprocessed;

    private final Stack stack;
    private final Memory memory;
    private final Repository repo;
    private final Repository originalRepo;
    private byte[] returnDataBuffer;

    private final Bytes ops;
    private int pc;
    private boolean stopped;

    public Program(Bytes ops, ProgramInvoke programInvoke, Spec spec) {
        this.ops = nullToEmpty(ops);
        this.invoke = programInvoke;
        this.result = ProgramResult.createEmptyResult(invoke.getGasLimit());

        this.memory = new Memory();
        this.stack = new Stack();
        this.repo = programInvoke.getRepository();
        this.originalRepo = programInvoke.getOriginalRepository();

        this.spec = spec;
    }

    public Program(Bytes ops, ProgramInvoke programInvoke) {
        this(ops, programInvoke, Spec.DEFAULT);
    }

    public ProgramPreprocess getProgramPreprocess() {
        if (preprocessed == null) {
            preprocessed = ProgramPreprocess.compile(ops);
        }
        return preprocessed;
    }

    public ProgramResult getResult() {
        return result;
    }

    public long getGasLeft() {
        return getResult().getGasLeft();
    }

    public long getGasUsed() {
        return getResult().getGasUsed();
    }

    private InternalTransaction addInternalTx(OpCode type, Bytes from, Bytes to, long nonce, BigInteger value,
            Bytes data, long gas) {

        int depth = getCallDepth();
        int index = getResult().getInternalTransactions().size();

        InternalTransaction tx = new InternalTransaction(depth, index, type.name(),
                from, to, nonce, value, data, gas, getGasPrice().value());
        getResult().addInternalTransaction(tx);

        return tx;
    }

    public byte getCurrentOp() {
//        return isEmpty(ops) ? 0 : ops.get(pc);
        return ops.isEmpty() ? 0 : ops.get(pc);
    }

    public void stackPush(Bytes data) {
        stackPush(DataWord.of(data));
    }

    public void stackPushZero() {
        stackPush(DataWord.ZERO);
    }

    public void stackPushOne() {
        stackPush(DataWord.ONE);
    }

    public void stackPush(DataWord stackWord) {
        verifyStackOverflow(0, 1); // Sanity Check
        stack.push(stackWord);
    }

    public Stack getStack() {
        return this.stack;
    }

    public int getPC() {
        return pc;
    }

    public void setPC(int pc) {
        this.pc = pc;

        if (this.pc >= ops.size()) {
            stop();
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    public void setHReturn(Bytes buff) {
        getResult().setReturnData(buff);
    }

    public void stop() {
        stopped = true;
    }

    public void step() {
        setPC(pc + 1);
    }

    public byte[] sweep(int n) {

        if (pc + n > ops.size())
            stop();

        byte[] data = Arrays.copyOfRange(ops.toArray(), pc, pc + n);
        pc += n;
        if (pc >= ops.size())
            stop();

        return data;
    }

    public DataWord stackPop() {
        return stack.pop();
    }

    /**
     * Verifies that the stack is at least <code>stackSize</code>
     *
     * @param stackSize
     *            int
     * @throws StackUnderflowException
     *             If the stack is smaller than <code>stackSize</code>
     */
    public void verifyStackUnderflow(int stackSize) throws StackUnderflowException {
        if (stack.size() < stackSize) {
            throw ExceptionFactory.tooSmallStack(stackSize, stack.size());
        }
    }

    public void verifyStackOverflow(int argsReqs, int returnReqs) {
        if ((stack.size() - argsReqs + returnReqs) > MAX_STACKSIZE) {
            throw ExceptionFactory.tooLargeStack((stack.size() - argsReqs + returnReqs), MAX_STACKSIZE);
        }
    }

    public int getMemSize() {
        return memory.size();
    }

    public void memorySave(DataWord addrB, DataWord value) {
        memory.write(addrB.intValue(), value.getData().toArray(), value.getData().size(), false);
    }

    public void memorySaveLimited(int addr, byte[] data, int dataSize) {
        memory.write(addr, data, dataSize, true);
    }

    public void memorySave(int addr, byte[] value) {
        memory.write(addr, value, value.length, false);
    }

    public void memoryExpand(DataWord outDataOffs, DataWord outDataSize) {
        if (!outDataSize.isZero()) {
            memory.extend(outDataOffs.intValue(), outDataSize.intValue());
        }
    }

    /**
     * Allocates a piece of memory and stores value at given offset address
     *
     * @param addr
     *            is the offset address
     * @param allocSize
     *            size of memory needed to write
     * @param value
     *            the data to write to memory
     */
    public void memorySave(int addr, int allocSize, Bytes value) {
        memory.extendAndWrite(addr, allocSize, value.toArray());
    }

    public DataWord memoryLoad(DataWord addr) {
        return memory.readWord(addr.intValue());
    }

    public byte[] memoryChunk(int offset, int size) {
        return memory.read(offset, size);
    }

    /**
     * Allocates extra memory in the program for a specified size, calculated from a
     * given offset
     *
     * @param offset
     *            the memory address offset
     * @param size
     *            the number of bytes to allocate
     */
    public void allocateMemory(int offset, int size) {
        memory.extend(offset, size);
    }

    public void suicide(DataWord beneficiary) {
        Bytes owner = getOwnerAddress().getLast20Bytes();
        Bytes obtainer = beneficiary.getLast20Bytes();
        BigInteger balance = getRepository().getBalance(owner);

        addInternalTx(OpCode.SUICIDE, owner, obtainer, getRepository().getNonce(owner), balance,
                Bytes.EMPTY, 0);

        if (owner.equals(obtainer)) {
            // if owner == obtainer just zeroing account according to Yellow Paper
            getRepository().addBalance(owner, balance.negate());
        } else {
            transfer(getRepository(), owner, obtainer, balance);
        }

        getResult().addDeleteAccount(this.getOwnerAddress().getLast20Bytes());
    }

    public Repository getRepository() {
        return this.repo;
    }

    public Repository getOriginalRepository() {
        return this.originalRepo;
    }

    /**
     * Create contract for {@link OpCode#CREATE}
     */
    public ProgramResult createContract(DataWord value, DataWord memStart, DataWord memSize, long gas) {
        resetReturnDataBuffer();

        Bytes senderAddress = this.getOwnerAddress().getLast20Bytes();
        BigInteger endowment = value.value();
        RuntimeException exception = verifyCall(senderAddress, endowment);
        if (exception != null) {
            // in case of insufficient balance or call is too deep,
            // throw an exception
            return ProgramResult.createExceptionResult(getGasLeft(), exception);
        }

        long nonce = getRepository().getNonce(senderAddress);
        Bytes contractAddress = Bytes.wrap(HashUtils.calcNewAddress(senderAddress.toArray(), nonce));
        Bytes programCode = Bytes.wrap(memoryChunk(memStart.intValue(), memSize.intValue()));

        ProgramResult callResult = createContractImpl(value, programCode, contractAddress, gas);
        setReturnDataBuffer(callResult.getReturnData().toArray());
        return callResult;
    }

    /**
     * Create contract for {@link OpCode#CREATE2}
     */
    public ProgramResult createContract2(DataWord value, DataWord memStart, DataWord memSize, DataWord salt, long gas) {
        resetReturnDataBuffer();

        Bytes senderAddress = this.getOwnerAddress().getLast20Bytes();
        BigInteger endowment = value.value();
        RuntimeException exception = verifyCall(senderAddress, endowment);
        if (exception != null) {
            // in case of insufficient balance or call is too deep,
            // throw an exception
            return ProgramResult.createExceptionResult(getGasLeft(), exception);
        }

        Bytes programCode = Bytes.wrap(memoryChunk(memStart.intValue(), memSize.intValue()));
        Bytes contractAddress = Bytes.wrap(HashUtils.calcSaltAddress(senderAddress, programCode, salt.getData()));

        ProgramResult callResult = createContractImpl(value, programCode, contractAddress, gas);
        setReturnDataBuffer(callResult.getReturnData().toArray());
        return callResult;
    }

    /**
     * Call a contract for {@link OpCode#CALL}, {@link OpCode#CALLCODE} or
     * {@link OpCode#DELEGATECALL}.
     */
    public ProgramResult callContract(OpCode type, long gas, DataWord codeAddress, DataWord value, DataWord inDataOffs,
            DataWord inDataSize, DataWord outDataOffs, DataWord outDataSize) {
        resetReturnDataBuffer();

        Bytes senderAddress = this.getOwnerAddress().getLast20Bytes();
        BigInteger endowment = value.value();
        RuntimeException exception = verifyCall(senderAddress, endowment);
        if (exception != null) {
            // in case of insufficient balance or call is too deep,
            // do nothing and refund the remaining gas
            refundGas(gas, "call revoked");
            return ProgramResult.createEmptyResult(gas);
        }

        MessageCall msg = new MessageCall(type, gas, codeAddress, value, inDataOffs, inDataSize,
                outDataOffs, outDataSize);
        PrecompiledContract contract = spec.getPrecompiledContracts().getContractForAddress(codeAddress);

        ProgramResult callResult = callContractImpl(msg, contract);
        setReturnDataBuffer(callResult.getReturnData().toArray());
        return callResult;
    }

    /**
     * All stages required to create contract on provided address after initial
     * check
     *
     * @param value
     *            Endowment
     * @param programCode
     *            Contract code
     * @param newAddress
     *            Contract address
     */
    private ProgramResult createContractImpl(DataWord value, Bytes programCode, Bytes newAddress, long gas) {
        Bytes senderAddress = this.getOwnerAddress().getLast20Bytes();
        boolean contractAlreadyExists = getRepository().exists(newAddress);

        // [1] SPEND GAS
        spendGas(gas, "internal call - create");

        // [2] UPDATE THE NONCE
        // (THIS STAGE IS NOT REVERTED BY ANY EXCEPTION)
        getRepository().increaseNonce(senderAddress);

        // track for reversibility when failure
        Repository track = getRepository().startTracking();

        // [3] SET THE NONCE OF NEW CONTRACT TO ONE?????
        track.increaseNonce(newAddress);

        // [4] TRANSFER THE BALANCE
        transfer(track, senderAddress, newAddress, value.value());

        // [5] COOK AN INTERNAL TRANSACTION
        InternalTransaction internalTx = addInternalTx(OpCode.CREATE, senderAddress, Bytes.EMPTY,
                getRepository().getNonce(senderAddress), value.value(), programCode, gas);
        if (log.isDebugEnabled()) {
            log.debug("CREATE: {}", internalTx);
        }

        // [6] EXECUTE THE CODE
        ProgramResult result;
        if (contractAlreadyExists) {
            result = ProgramResult.createExceptionResult(gas,
                    new BytecodeExecutionException("Account already exists: 0x" + newAddress.toHexString()));
        } else if (!programCode.isEmpty()) {
            ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(this,
                    getOwnerAddress(),
                    DataWord.of(newAddress),
                    gas,
                    value,
                    Bytes.EMPTY,
                    track,
                    this.invoke.getBlockStore(),
                    false);
            Program program = new Program(programCode, programInvoke, spec);

            new EVM(spec).play(program);
            result = program.getResult();
        } else {
            result = ProgramResult.createEmptyResult(gas);
        }

        // [7] SAVE THE CONTRACT CODE
        if (result.getException() == null && !result.isRevert()) {
            Bytes code = result.getReturnData();
            long storageCost = (long) code.size() * spec.getFeeSchedule().getCREATE_DATA();

            if (result.getGasLeft() < storageCost) {
                if (!spec.createEmptyContractOnOOG()) {
                    result.setReturnData(Bytes.EMPTY);
                    result.setException(ExceptionFactory.notEnoughSpendingGas("No gas to return just created contract",
                            storageCost, this));
                } else {
                    // free of charge; return data un-touched
                    track.saveCode(newAddress, Bytes.EMPTY);
                }
            } else if (code.size() > spec.maxContractSize()) {
                result.setReturnData(Bytes.EMPTY);
                result.setException(ExceptionFactory.notEnoughSpendingGas("Contract size too large: "
                        + result.getReturnData().size(), storageCost, this));
            } else {
                result.spendGas(storageCost);
                track.saveCode(newAddress, code);
            }
        }

        // [8] POST EXECUTION PROCESSING
        if (result.getException() == null && !result.isRevert()) {
            // commit changes
            track.commit();
            // in success push the address into the stack
            stackPush(DataWord.of(newAddress));
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Contract run halted by Exception: contract: [{}], exception: [{}]",
                        newAddress.toHexString(),
                        result.getException());
            }

            internalTx.reject();
            result.rejectInternalTransactions();

            track.rollback();
            stackPushZero();
        }

        // [9] REFUND THE REMAIN GAS
        if (result.getException() == null) {
            long refundGas = result.getGasLeft();
            if (refundGas > 0) {
                refundGas(refundGas, "remaining gas from create");
            }
        }

        // [10] MERGE RESULT INTO PARENT
        getResult().merge(result);

        return result;
    }

    /**
     * Makes an internal call to another address.
     *
     * Note: normal calls invoke a specified contract which updates itself, while
     * Stateless calls invoke code from another contract, within the context of the
     * caller.
     *
     * @param msg
     *            the message call object
     * @param contract
     *            the called precompiled contract, can be NULL
     */
    private ProgramResult callContractImpl(MessageCall msg, PrecompiledContract contract) {
        Bytes codeAddress = msg.getCodeAddress().getLast20Bytes();
        Bytes senderAddress = getOwnerAddress().getLast20Bytes();
        Bytes contextAddress = msg.getType().callIsStateless() ? senderAddress : codeAddress;
        Bytes data = Bytes.wrap(memoryChunk(msg.getInDataOffs().intValue(), msg.getInDataSize().intValue()));
        BigInteger endowment = msg.getEndowment().value();

        // track for reversibility when failure
        Repository track = getRepository().startTracking();

        // [4] TRANSFER THE BALANCE
        transfer(track, senderAddress, contextAddress, endowment);

        // [5] COOK AN INTERNAL TRANSACTION AND INVOKE
        InternalTransaction internalTx = addInternalTx(msg.getType(), senderAddress, contextAddress,
                getRepository().getNonce(senderAddress), endowment, data, msg.getGas());
        if (log.isDebugEnabled()) {
            log.debug("CALL: {}", internalTx);
        }

        // [6] EXECUTE THE CODE
        ProgramResult result;
        if (contract != null) {
            long requiredGas = contract.getGasForData(data);
            if (requiredGas > msg.getGas()) {
                track.rollback();
                stackPushZero();
                result = ProgramResult.createExceptionResult(msg.getGas(),
                        new OutOfGasException("Precompiled out-of-gas"));
            } else {
                result = ProgramResult.createEmptyResult(msg.getGas());
                result.spendGas(requiredGas);
                Pair<Boolean, Bytes> out = contract.execute(new PrecompiledContractContext() {
                    @Override
                    public Repository getTrack() {
                        return track;
                    }

                    @Override
                    public ProgramResult getResult() {
                        return result;
                    }

                    @Override
                    public InternalTransaction getInternalTransaction() {
                        return internalTx;
                    }
                });
                if (!out.getLeft()) {
                    result.setReturnData(Bytes.EMPTY);
                    result.setException(new PrecompiledFailureException());
                } else {
                    result.setReturnData(Bytes.wrap(out.getRight()));
                }
            }
        } else {
            Bytes programCode = getRepository().getCode(codeAddress);
            if (programCode!= null && !programCode.isEmpty()) {
                ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(this,
                        msg.getType().callIsDelegate() ? getCallerAddress() : getOwnerAddress(),
                        DataWord.of(contextAddress),
                        msg.getGas(),
                        msg.getType().callIsDelegate() ? getCallValue() : msg.getEndowment(),
                        data,
                        track,
                        this.invoke.getBlockStore(),
                        msg.getType().callIsStatic() || isStaticCall());
                Program program = new Program(programCode, programInvoke, spec);
                new EVM(spec).play(program);
                result = program.getResult();
            } else {
                result = ProgramResult.createEmptyResult(msg.getGas());
            }
        }

        // [7] POST EXECUTION PROCESSING
        if (result.getException() == null && !result.isRevert()) {
            // commit changes
            track.commit();
            // in success push one into the stack
            stackPushOne();
        } else {
            if (log.isDebugEnabled()) {
                log.debug("contract run halted by Exception: contract: [{}], exception: [{}]",
                        contextAddress.toHexString(),
                        result.getException());
            }

            internalTx.reject();
            result.rejectInternalTransactions();

            track.rollback();
            stackPushZero();
        }

        // APPLY RESULTS: result.getReturnData() into out_memory allocated
        if (!result.getReturnData().isEmpty()) {
            Bytes buffer = result.getReturnData();
            int offset = msg.getOutDataOffs().intValue();
            int size = msg.getOutDataSize().intValue();

            memorySaveLimited(offset, buffer.toArray(), size);
        }

        // [8] REFUND THE REMAIN GAS
        if (result.getException() == null) {
            long refundGas = result.getGasLeft();
            if (refundGas > 0) {
                refundGas(refundGas, "remaining gas from call");
            }
        }

        // [9] MERGE RESULT INTO PARENT
        getResult().merge(result);

        return result;
    }

    public void spendGas(long gasValue, String cause) {
        log.debug("Spend: cause = [{}], gas = [{}]", cause, gasValue);

        if (getGasLeft() < gasValue) {
            throw ExceptionFactory.notEnoughSpendingGas(cause, gasValue, this);
        }
        getResult().spendGas(gasValue);
    }

    public void spendAllGas() {
        spendGas(getGasLeft(), "consume all");
    }

    public void refundGas(long gasValue, String cause) {
        log.debug("Refund: cause = [{}], gas = [{}]", cause, gasValue);

        getResult().refundGas(gasValue);
    }

    public void futureRefundGas(long gasValue) {
        log.debug("Future refund added: [{}]", gasValue);

        getResult().addFutureRefund(gasValue);
    }

    public void resetFutureRefund() {
        getResult().resetFutureRefund();
    }

    public void storageSave(DataWord key, DataWord value) {
        getRepository().putStorageRow(getOwnerAddress().getLast20Bytes(), key, value);
    }

    public Bytes getCode() {
        return ops;
    }

    public Bytes getCodeAt(DataWord address) {
        Bytes code = invoke.getRepository().getCode(address.getLast20Bytes());
        return nullToEmpty(code);
    }

    public DataWord getOwnerAddress() {
        return invoke.getOwnerAddress();
    }

    public DataWord getBlockHash(int index) {
        return index < this.getBlockNumber().longValue()
                && index >= Math.max(256, this.getBlockNumber().intValue()) - 256
                        ? DataWord.of(this.invoke.getBlockStore().getBlockHashByNumber(index))
                        : DataWord.ZERO;
    }

    public DataWord getBalance(DataWord address) {
        BigInteger balance = getRepository().getBalance(address.getLast20Bytes());
        return DataWord.of(Bytes.wrap(balance.toByteArray()));
    }

    public DataWord getOriginAddress() {
        return invoke.getOriginAddress();
    }

    public DataWord getCallerAddress() {
        return invoke.getCallerAddress();
    }

    public DataWord getGasPrice() {
        return invoke.getGasPrice();
    }

    public DataWord getCallValue() {
        return invoke.getValue();
    }

    public DataWord getDataSize() {
        return invoke.getDataSize();
    }

    public DataWord getDataValue(DataWord index) {
        return invoke.getDataValue(index);
    }

    public Bytes getDataCopy(DataWord offset, DataWord length) {
        return invoke.getDataCopy(offset, length);
    }

    public DataWord getReturnDataBufferSize() {
        return DataWord.of(getReturnDataBufferSizeI());
    }

    public byte[] getReturnDataBufferData(DataWord off, DataWord size) {
        if ((long) off.intValueSafe() + size.intValueSafe() > getReturnDataBufferSizeI())
            return null;
        return returnDataBuffer == null ? EMPTY_BYTE_ARRAY
                : Arrays.copyOfRange(returnDataBuffer, off.intValueSafe(), off.intValueSafe() + size.intValueSafe());
    }

    /**
     * Returns the current storage data for key
     */
    public DataWord getCurrentStorageValue(DataWord key) {
        return getRepository().getStorageRow(getOwnerAddress().getLast20Bytes(), key);
    }

    /**
     * Returns the storage data at the beginning of program execution
     */
    public DataWord getOriginalStorageValue(DataWord key) {
        return getOriginalRepository().getStorageRow(getOwnerAddress().getLast20Bytes(), key);
    }

    public DataWord getBlockCoinbase() {
        return invoke.getBlockCoinbase();
    }

    public DataWord getBlockTimestamp() {
        return invoke.getBlockTimestamp();
    }

    public DataWord getBlockNumber() {
        return invoke.getBlockNumber();
    }

    public DataWord getBlockDifficulty() {
        return invoke.getBlockDifficulty();
    }

    public DataWord getBlockGasLimit() {
        return invoke.getBlockGasLimit();
    }

    public DataWord getBlockPrevHash() {
        return invoke.getBlockPrevHash();
    }

    public int getCallDepth() {
        return invoke.getCallDepth();
    }

    public boolean isStaticCall() {
        return invoke.isStaticCall();
    }

    public void setException(RuntimeException e) {
        getResult().setException(e);
    }

    public void setRevert(boolean isRevert) {
        getResult().setRevert(isRevert);
    }

    public int verifyJumpDest(DataWord nextPC) {
        if (nextPC.bytesOccupied() > 4) {
            throw ExceptionFactory.badJumpDestination(-1);
        }
        int ret = nextPC.intValue();
        if (!getProgramPreprocess().hasJumpDest(ret)) {
            throw ExceptionFactory.badJumpDestination(ret);
        }
        return ret;
    }

    /**
     * used mostly for testing reasons
     */
    public byte[] getMemory() {
        return memory.read(0, memory.size());
    }

    /**
     * used mostly for testing reasons
     */
    public void initMem(byte[] data) {
        this.memory.write(0, data, data.length, false);
    }

    private void resetReturnDataBuffer() {
        returnDataBuffer = null;
    }

    private void setReturnDataBuffer(byte[] newReturnData) {
        returnDataBuffer = newReturnData;
    }

    private int getReturnDataBufferSizeI() {
        return returnDataBuffer == null ? 0 : returnDataBuffer.length;
    }

    private RuntimeException verifyCall(Bytes senderAddress, BigInteger endowment) {
        if (getCallDepth() == MAX_DEPTH) {
            stackPushZero();
            return new CallTooDeepException();
        }

        if (isNotCovers(getRepository().getBalance(senderAddress), endowment)) {
            stackPushZero();
            return new InsufficientBalanceException();
        }

        return null;
    }
}
