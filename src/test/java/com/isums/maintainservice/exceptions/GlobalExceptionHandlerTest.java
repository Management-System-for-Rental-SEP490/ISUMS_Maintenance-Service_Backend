package com.isums.maintainservice.exceptions;

import com.isums.maintainservice.domains.dtos.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NotFoundException -> 404")
    void notFound() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleNotFound(new NotFoundException("gone"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().getMessage()).isEqualTo("gone");
    }

    @Test
    @DisplayName("custom BadRequestException -> 400")
    void customBadRequest() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleBadRequest(new BadRequestException("bad"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("IllegalArgumentException -> 400")
    void illegalArg() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleIllegalArg(new IllegalArgumentException("x"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("DataAccessException -> 500 with DB_ERROR code")
    void db() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleDb(new DataAccessResourceFailureException("down"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().getErrors().getFirst().getCode()).isEqualTo("DB_ERROR");
    }

    @Test
    @DisplayName("unhandled Exception -> 500 with generic message")
    void generic() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleGeneric(new RuntimeException("boom"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().getMessage()).isEqualTo("Unexpected error");
    }
}
