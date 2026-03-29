package com.fever.challenge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard API response wrapper matching the OpenAPI spec.
 * All responses have a "data" and "error" field.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private T data;
    private ErrorDto error;

    private ApiResponse(T data, ErrorDto error) {
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, new ErrorDto(code, message));
    }

    public T getData() { return data; }
    public ErrorDto getError() { return error; }

    public static class ErrorDto {
        private String code;
        private String message;

        public ErrorDto(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}
