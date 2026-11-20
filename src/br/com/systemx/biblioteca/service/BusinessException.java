package br.com.systemx.biblioteca.service;

public class BusinessException extends RuntimeException {
    private final int statusCode;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
