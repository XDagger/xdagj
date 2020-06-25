package io.xdag.core;

import io.xdag.crypto.ECKey;
import io.xdag.utils.XdagTime;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PoWTest {

  @Test
  public void powBlock() {
    String blockRawdata =
        "000000000000000038324654050000004d3782fa780100000000000000000000"
            + "c86357a2f57bb9df4f8b43b7a60e24d1ccc547c606f2d7980000000000000000"
            + "afa5fec4f56f7935125806e235d5280d7092c6840f35b397000000000a000000"
            + "a08202c3f60123df5e3a973e21a2dd0418b9926a2eb7c4fc000000000a000000"
            + "08b65d2e2816c0dea73bf1b226c95c2ae3bc683574f559bbc5dd484864b1dbeb"
            + "f02a041d5f7ff83a69c0e35e7eeeb64496f76f69958485787d2c50fd8d9614e6"
            + "7c2b69c79eddeff5d05b2bfc1ee487b9c691979d315586e9928c04ab3ace15bb"
            + "3866f1a25ed00aa18dde715d2a4fc05147d16300c31fefc0f3ebe4d77c63fcbb"
            + "ec6ece350f6be4c84b8705d3b49866a83986578a3a20e876eefe74de0c094bac"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000";
    Block first = new Block(new XdagBlock(Hex.decode(blockRawdata)));

    System.out.println(
        "=====================================first block use key1========================================");

    long time = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp()); // extra
    List<Address> pending = new ArrayList<>();
    pending.add(new Address(first.getHashLow()));
    Block txfirst = new Block(time, first.getFirstOutput(), null, pending, false, null, -1);
    ECKey ecKey1 = new ECKey();
    txfirst.signOut(ecKey1);

    System.out.println(
        "=====================================second block use key2========================================");
    time = XdagTime.getEndOfEpoch(XdagTime.getCurrentTimestamp()); // extra
    pending = new ArrayList<>();
    pending.add(new Address(first.getHashLow()));
    Block txsecond = new Block(time, first.getFirstOutput(), null, pending, false, null, -1);
    ECKey ecKey2 = new ECKey();
    txsecond.signOut(ecKey2);
    printBlockInfo(txsecond);

    System.out.println(
        "=====================================main block use key2========================================");
    pending = new ArrayList<>();
    pending.add(new Address(txfirst.getHashLow()));
    pending.add(new Address(txsecond.getHashLow()));
    Block main =
        new Block(time, new Address(first.getHashLow()), null, pending, true, null, -1); // extra
    main.signOut(ecKey2);
    byte[] minShare = new byte[32];
    new Random().nextBytes(minShare);
    main.setNonce(minShare);
    printBlockInfo(main);
    System.out.println(
        "=====================================main block use key2========================================");

    new Random().nextBytes(minShare);
    main.setNonce(minShare);
    printBlockInfo(main);
  }

  public void printBlockInfo(Block block) {
    System.out.println("timestamp:" + Long.toHexString(block.getTimestamp()));
    printHash(block.getHash(), "blockhash:");
    printHash(block.getHashLow(), "blockhashlow:");
    System.out.println("type:" + block.getType());
    if (block.getFirstOutput() != null)
      printHash(block.getFirstOutput().getHashLow(), "firstoutput:");
    System.out.println("inputs:" + block.getInputs().size());
    printListAddress(block.getInputs());
    System.out.println("outputs:" + block.getOutputs().size());
    printListAddress(block.getOutputs());
    System.out.println("keys size:");
    System.out.println(block.getPubKeys().size());
    System.out.println("verified keys size");
    System.out.println(block.verifiedKeys().size());
    System.out.println("blockdiff:" + block.getDifficulty());
    printXdagBlock(block.getXdagBlock(), "xdagblock:");
    printListKeys(block.getPubKeys());
    printHash(block.getOutsig().toByteArray(), "outsig:");
    System.out.println("outsigindex:" + block.getOutsigIndex());
    printMapInsig(block.getInsigs());
    if (block.getNonce() != null) {
      System.out.println("nonce:" + Hex.toHexString(block.getNonce()));
    }
  }

  public void printXdagBlock(XdagBlock block, String prefix) {
    System.out.println(prefix);
    for (XdagField field : block.getFields()) {
      System.out.println(Hex.toHexString(field.getData()));
    }
  }

  public void printMapInsig(Map<ECKey.ECDSASignature, Integer> input) {
    for (ECKey.ECDSASignature sig : input.keySet()) {
      System.out.println("inputsig:" + sig.toHex());
      System.out.println("inputsigindex:" + input.get(sig));
    }
  }

  public void printHash(byte[] hash, String prefix) {
    System.out.println(prefix + Hex.toHexString(hash));
  }

  public void printListAddress(List<Address> input) {
    for (Address address : input) {
      System.out.println("address data:" + Hex.toHexString(address.getData()));
      System.out.println("address hashlow:" + Hex.toHexString(address.getHashLow()));
      System.out.println("address amount:" + address.getAmount());
    }
  }

  public void printListKeys(List<ECKey> input) {
    for (ECKey ecKey : input) {
      printHash(ecKey.getPubKeybyCompress(), "key:");
    }
  }

  @Test
  public void cloneByteArray() {
    byte[] hash = Hex.decode("0000000000000000a8c0390b57648f09aeba60e72ad9623c589a951ec3110af3");

    byte[] hash2 = hash.clone();

    System.out.println(hash);
    System.out.println(hash2);
  }
}
