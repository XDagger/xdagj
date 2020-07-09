package io.xdag.config;

import static org.junit.Assert.*;

import org.junit.Test;

public class ConfigTest {

  @Test
  public void getSettingTest() {
    Config config = new Config();
    config.getSetting();
    StringBuilder str = new StringBuilder();
    str.append(config.toString());
    System.out.println(str.toString());
  }

}