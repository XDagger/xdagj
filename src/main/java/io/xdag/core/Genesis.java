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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.xdag.Network;
import io.xdag.core.state.ByteArray;
import io.xdag.utils.BytesUtils;
import io.xdag.utils.WalletUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
public class Genesis extends MainBlock {

    @JsonDeserialize(keyUsing = ByteArray.ByteArrayKeyDeserializer.class)
    private final Map<ByteArray, XSnapshot> snapshots;

    private final Map<String, Object> config;

    /**
     * Creates a {@link Genesis} block instance.
     */
    public Genesis(BlockHeader header, Map<ByteArray, XSnapshot> snapshots, Map<String, Object> config) {
        super(header, Collections.emptyList(), Collections.emptyList());

        this.snapshots = snapshots;
        this.config = config;
    }

    @JsonCreator
    public static Genesis jsonCreator(
            @JsonProperty("number") long number,
            @JsonProperty("coinbase") String coinbase,
            @JsonProperty("parentHash") String parentHash,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("data") String data,
            @JsonProperty("snapshot") List<XSnapshot> snapshotsList,
            @JsonProperty("config") Map<String, Object> config) {

        // load snapshot
        Map<ByteArray, XSnapshot> snapshotMap = new HashMap<>();
        for (XSnapshot snapshot : snapshotsList) {
            snapshotMap.put(new ByteArray(snapshot.getAddress()), snapshot);
        }

        // load block header
        BlockHeader header = new BlockHeader(number,
                Bytes.fromHexString(coinbase).toArray(),
                Bytes.fromHexString(parentHash).toArray(),
                timestamp,
                BytesUtils.EMPTY_HASH,
                BytesUtils.EMPTY_HASH,
                0L,
                BytesUtils.of(data));

        return new Genesis(header, snapshotMap, config);
    }

    /**
     * Loads the genesis file.
     */
    public static Genesis load(Network network) {
        try {
            InputStream in = Genesis.class.getResourceAsStream("/genesis/" + network.label() + ".json");

            if (in != null) {
                return new ObjectMapper().readValue(in, Genesis.class);
            }
        } catch (IOException e) {
            log.error("Failed to load genesis file", e);
        }

        //SystemUtil.exitAsync(SystemUtil.Code.FAILED_TO_LOAD_GENESIS);
        return null;
    }

    @Getter
    public static class XSnapshot {
        private final byte[] address;
        private final XAmount amount;
        private final String note;

        public XSnapshot(byte[] address, XAmount amount, String note) {
            this.address = address;
            this.amount = amount;
            this.note = note;
        }

        @JsonCreator
        public XSnapshot(@JsonProperty("address") String address,
                         @JsonProperty("amount") long amount,
                @JsonProperty("note") String note) {
            this(WalletUtils.fromBase58(address), XAmount.of(amount), note);
        }

    }
}
