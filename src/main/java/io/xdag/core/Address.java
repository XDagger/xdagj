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

package io.xdag.core;

import io.xdag.crypto.encoding.Base58;
import io.xdag.utils.BytesUtils;
import lombok.Getter;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;

public class Address {

    /**
     * Data to be placed in the field in normal order
     */
    protected MutableBytes32 data;
    
    /**
     * Field type: input/output/output without amount
     */
    @Getter
    @Setter
    protected XdagField.FieldType type;
    
    /**
     * Transfer amount (input or output)
     */
    protected XAmount amount = XAmount.ZERO;
    
    /**
     * Lower 192 bits of address hash
     */
    protected MutableBytes32 addressHash;

    /**
     * Flag indicating if this is an address
     */
    protected boolean isAddress;

    /**
     * Flag indicating if the address has been parsed
     */
    protected boolean parsed = false;

    public Address(XdagField field, Boolean isAddress) {
        this.isAddress = isAddress;
        this.type = field.getType();
        this.data = MutableBytes32.wrap(field.getData().reverse().mutableCopy());
        parse();
    }

    /**
     * Constructor used only for ref and maxdifflink
     */
    public Address(Bytes32 hashLow, boolean isAddress) {
        this.isAddress = isAddress;
        this.type = XdagField.FieldType.XDAG_FIELD_OUT;
        addressHash = MutableBytes32.create();
        if(!isAddress){
            this.addressHash = hashLow.mutableCopy();
        }else {
            this.addressHash.set(8,hashLow.mutableCopy().slice(8,20));
        }
        this.amount = XAmount.ZERO;
        parsed = true;
    }

    /**
     * Constructor used only for ref and maxdifflink
     */
    public Address(Block block) {
        this.isAddress = false;
        this.addressHash = block.getHashLow().mutableCopy();
        parsed = true;
    }

    public Address(Bytes32 blockHashlow, XdagField.FieldType type, Boolean isAddress) {
        this.isAddress = isAddress;
        if(!isAddress){
            this.data = blockHashlow.mutableCopy();
        }else {
            this.data = MutableBytes32.create();
            data.set(8,blockHashlow.mutableCopy().slice(8,20));
        }
        this.type = type;
        parse();
    }

    public Address(Bytes32 hash, XdagField.FieldType type, XAmount amount, Boolean isAddress) {
        this.isAddress = isAddress;
        this.type = type;
        if(!isAddress){
            this.addressHash = hash.mutableCopy();
        }else {
            this.addressHash = MutableBytes32.create();
            this.addressHash.set(8,hash.mutableCopy().slice(8,20));
        }
        this.amount = amount;
        parsed = true;
    }

    public Bytes getData() {
        if (this.data == null) {
            this.data = MutableBytes32.create();
            if(!this.isAddress){
                this.data.set(8, this.addressHash.slice(8, 24));
            }else {
                this.data.set(8, this.addressHash.slice(8,20));
            }
            UInt64 u64v = amount.toXAmount();
            this.data.set(0, Bytes.wrap(BytesUtils.bigIntegerToBytes(u64v,8)));
        }
        return this.data;
    }

    public void parse() {
        if (!parsed) {
            if(!isAddress){
                this.addressHash = MutableBytes32.create();
                this.addressHash.set(8, this.data.slice(8, 24));
            }else {
                this.addressHash = MutableBytes32.create();
                this.addressHash.set(8,this.data.slice(8,20));
            }
            UInt64 u64v = UInt64.fromBytes(this.data.slice(0, 8));
            this.amount = XAmount.ofXAmount(u64v.toLong());
            this.parsed = true;
        }
    }

    public XAmount getAmount() {
        parse();
        return this.amount;
    }

    public MutableBytes32 getAddress() {
        parse();
        return this.addressHash;
    }

    public boolean getIsAddress() {
        parse();
        return this.isAddress;
    }

    @Override
    public String toString() {
        if(isAddress){
            return "Address [" + Base58.encodeCheck(addressHash.slice(8,20)) + "]";
        }else {
            return "Block Hash[" + addressHash.toHexString() + "]";
        }
    }
}
