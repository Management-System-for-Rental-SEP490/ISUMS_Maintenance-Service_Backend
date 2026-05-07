package com.isums.maintainservice.exceptions;

import com.isums.maintainservice.domains.dtos.ApiError;
import com.isums.maintainservice.domains.dtos.ApiResponse;
import com.isums.maintainservice.domains.dtos.ApiResponses;
import com.isums.maintainservice.infrastructures.i18n.MessageTranslator;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageTranslator messages;

    public GlobalExceptionHandler(MessageTranslator messages) {
        this.messages = messages;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        String msg = messages.resolve(ex.getMessageKey(), ex.getMessageArgs());
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.NOT_FOUND,
                msg,
                List.of(ApiError.builder().code("NOT_FOUND").message(msg).build())
        );
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        String msg = messages.resolve(ex.getMessageKey(), ex.getMessageArgs());
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.BAD_REQUEST,
                msg,
                List.of(ApiError.builder().code("BAD_REQUEST").message(msg).build())
        );
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException ex) {
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                List.of(ApiError.builder().code("BAD_REQUEST").message(ex.getMessage()).build())
        );
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDb(DataAccessException ex) {
        String detail = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();

        String localized = messages.resolve("maintenance.errors.databaseError");
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                localized,
                List.of(ApiError.builder().code("DB_ERROR").message(detail).build())
        );
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        String localized = messages.resolve("maintenance.errors.unexpectedError");
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                localized,
                List.of(ApiError.builder().code("INTERNAL_ERROR").message(ex.getMessage()).build())
        );
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
