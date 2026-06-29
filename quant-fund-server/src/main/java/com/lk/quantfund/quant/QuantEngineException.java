package com.lk.quantfund.quant;

public class QuantEngineException extends RuntimeException {

    public QuantEngineException(String message) {
        super(message);
    }

    public QuantEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
