package io.xdag.core;


import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;

@Getter
@Setter
public class XdagTopStatus {

    private byte[] top;
    private BigInteger topDiff;
    private byte[] preTop;
    private BigInteger preTopDiff;

    public XdagTopStatus(){
        topDiff = BigInteger.ZERO;
        preTopDiff = BigInteger.ZERO;
    }

}
