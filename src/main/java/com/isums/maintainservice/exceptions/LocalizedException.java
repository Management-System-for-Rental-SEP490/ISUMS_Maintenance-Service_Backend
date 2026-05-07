package com.isums.maintainservice.exceptions;

public abstract class LocalizedException extends RuntimeException {

    private final String messageKey;
    private final Object[] messageArgs;

    protected LocalizedException(String messageKey, Object... messageArgs) {
        super(messageKey);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
