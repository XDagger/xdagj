package io.xdag.net.message;

public class MessageRoundtrip {
  private final Message msg;
  long lastTimestamp = 0;
  long retryTimes = 0;
  boolean answered = false;

  public MessageRoundtrip(Message msg) {
    this.msg = msg;
    saveTime();
  }

  public boolean isAnswered() {
    return answered;
  }

  public void answer() {
    answered = true;
  }

  public long getRetryTimes() {
    return retryTimes;
  }

  public void incRetryTimes() {
    ++retryTimes;
  }

  public void saveTime() {
    lastTimestamp = System.currentTimeMillis();
  }

  public boolean hasToRetry() {
    return 20000 < System.currentTimeMillis() - lastTimestamp;
  }

  public Message getMsg() {
    return msg;
  }
}
