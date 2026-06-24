package com.lk.quantfund.datasource;

public class FundDataSourceException extends RuntimeException {

    public FundDataSourceException(String message) {
        super(message);
    }

    public FundDataSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}

