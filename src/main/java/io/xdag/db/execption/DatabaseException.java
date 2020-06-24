package io.xdag.db.execption;

/**
 * @ClassName DatabaseException
 * @Description
 * @Author punk
 * @Date 2020/4/18 00:48
 * @Version V1.0
 **/

public class DatabaseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DatabaseException() {
    }

    public DatabaseException(String s) {
        super(s);
    }

    public DatabaseException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public DatabaseException(Throwable throwable) {
        super(throwable);
    }

    public DatabaseException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
