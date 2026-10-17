package com.example.pickdispatch.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int status;
    private final String code;

    public BusinessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, "NOT_FOUND", message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(400, "BAD_REQUEST", message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(409, code, message);
    }
}
