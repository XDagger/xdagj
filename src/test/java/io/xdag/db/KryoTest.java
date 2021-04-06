package io.xdag.db;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.google.common.primitives.UnsignedLong;
import io.xdag.core.BlockInfo;
import io.xdag.db.execption.DeserializationException;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;

public class KryoTest {
    private Kryo kryo;

    @Before
    public void init() {
        kryo = new Kryo();
        kryo.register(BigInteger.class);
        kryo.register(byte[].class);
        kryo.register(BlockInfo.class);
    }

    @Test
    public void deserialize() {
        String data = "000b0b06a3b82241967b51a190003821c85e4170076aaca3b2ca5157c4e32be33164847e5a4ab0b03abe6202b0cd2712210000000000000000b2ca5157c4e32be33164847e5a4ab0b03abe6202b0cd271200210000000000000000ed08bcea6ac58a3cc883ad35e862caf1e60fe8f77d0933ba210000000000000000b8cb3358f9fbca51916d3d7378b00190dc75b3e55703180f00feff97d8f85bf0d482808080808080";
        byte[] input = Hex.decode(data);
        try {
            BlockInfo blockInfo = (BlockInfo) deserialize(input,BlockInfo.class);
            System.out.println(blockInfo.getDifficulty());
        } catch (DeserializationException e) {
            e.printStackTrace();
        }


    }

    private Object deserialize(final byte[] bytes, Class<?> type) throws DeserializationException {
        try {
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            final Input input = new Input(inputStream);
            return kryo.readObject(input, type);
        } catch (final IllegalArgumentException | KryoException | NullPointerException exception ) {
            System.out.println("Deserialize data:"+Hex.toHexString(bytes));
            throw new DeserializationException(exception.getMessage(), exception);
        }
    }

}
