package io.xdag.discovery.Utils;

import io.xdag.discovery.Utils.bytes.uint.RLPException;

public class MalformedRLPInputException extends RLPException {
    public MalformedRLPInputException(final String message) {
        super(message);
    }
}