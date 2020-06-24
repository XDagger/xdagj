package io.xdag.mine.miner;

import org.junit.Test;

public class MinerCalculateTest {

    @Test
    public void movingAverageDouble() {

        double res = MinerCalculate.movingAverageDouble(100,200,300);
        System.out.println(res);
    }

    @Test
    public void minerCalculateUnpaidSharesTest() {
        double sum = 344.0293;
        int count =5;

        double res = MinerCalculate.diffToPay(sum,count);
        System.out.println(res);

    }
}