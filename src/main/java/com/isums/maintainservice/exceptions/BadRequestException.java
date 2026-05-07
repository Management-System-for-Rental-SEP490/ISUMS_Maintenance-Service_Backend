package com.isums.maintainservice.exceptions;

public class BadRequestException extends LocalizedException {

    public BadRequestException(String messageKey) {
        super(messageKey);
    }

    public BadRequestException(String messageKey, Object... args) {
        super(messageKey, args);
    }
}
