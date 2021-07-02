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

/**
 * A wrapper for a message call from a contract to another account.
 */
public class MessageCall {

    /**
     * Type of internal call. Either CALL, CALLCODE or POST
     */
    private final OpCode type;

    /**
     * gas to pay for the call, remaining gas will be refunded to the caller
     */
    private final long gas;

    /**
     * address of account which code to call
     */
    private final DataWord codeAddress;

    /**
     * the value that can be transfer along with the code execution
     */
    private final DataWord endowment;

    /**
     * start of memory to be input data to the call
     */
    private final DataWord inDataOffs;

    /**
     * size of memory to be input data to the call
     */
    private final DataWord inDataSize;

    /**
     * start of memory to be output of the call
     */
    private final DataWord outDataOffs;

    /**
     * size of memory to be output data to the call
     */
    private final DataWord outDataSize;

    public MessageCall(OpCode type, long gas, DataWord codeAddress,
                       DataWord endowment, DataWord inDataOffs, DataWord inDataSize,
                       DataWord outDataOffs, DataWord outDataSize) {
        this.type = type;
        this.gas = gas;
        this.codeAddress = codeAddress;
        this.endowment = endowment;
        this.inDataOffs = inDataOffs;
        this.inDataSize = inDataSize;
        this.outDataOffs = outDataOffs;
        this.outDataSize = outDataSize;
    }

    public OpCode getType() {
        return type;
    }

    public long getGas() {
        return gas;
    }

    public DataWord getCodeAddress() {
        return codeAddress;
    }

    public DataWord getEndowment() {
        return endowment;
    }

    public DataWord getInDataOffs() {
        return inDataOffs;
    }

    public DataWord getInDataSize() {
        return inDataSize;
    }

    public DataWord getOutDataOffs() {
        return outDataOffs;
    }

    public DataWord getOutDataSize() {
        return outDataSize;
    }
}

