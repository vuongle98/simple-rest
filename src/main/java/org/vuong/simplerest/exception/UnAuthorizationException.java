package org.vuong.simplerest.exception;

public class UnAuthorizationException extends RuntimeException {

    public UnAuthorizationException(String message) {
        super(message);
    }
}
