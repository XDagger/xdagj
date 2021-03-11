package io.xdag.utils.discoveryutils;

import io.xdag.utils.discoveryutils.bytes.uint.RLPException;

public class MalformedRLPInputException extends RLPException {
    public MalformedRLPInputException(final String message) {
        super(message);
    }
}