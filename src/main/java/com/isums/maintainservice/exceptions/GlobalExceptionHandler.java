package com.isums.maintainservice.exceptions;

import com.isums.maintainservice.domains.dtos.ApiError;
import com.isums.maintainservice.domains.dtos.ApiResponse;
import com.isums.maintainservice.domains.dtos.ApiResponses;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                List.of(ApiError.builder().code("NOT_FOUND").message(ex.getMessage()).build())
        );
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                List.of(ApiError.builder().code("BAD_REQUEST").message(ex.getMessage()).build())
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

        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Database error",
                List.of(ApiError.builder().code("DB_ERROR").message(detail).build())
        );
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                List.of(ApiError.builder().code("INTERNAL_ERROR").message(ex.getMessage()).build())
        );
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
