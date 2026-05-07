package com.isums.maintainservice.exceptions;

public class NotFoundException extends LocalizedException {

    public NotFoundException(String messageKey) {
        super(messageKey);
    }

    public NotFoundException(String messageKey, Object... args) {
        super(messageKey, args);
    }
}
