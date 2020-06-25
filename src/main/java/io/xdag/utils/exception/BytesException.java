package io.xdag.utils.exception;

public class BytesException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public BytesException() {}

  public BytesException(String s) {
    super(s);
  }

  public BytesException(String s, Throwable throwable) {
    super(s, throwable);
  }

  public BytesException(Throwable throwable) {
    super(throwable);
  }

  public BytesException(String s, Throwable throwable, boolean b, boolean b1) {
    super(s, throwable, b, b1);
  }
}
