package com.pasarela.api;

import com.pasarela.config.RequestIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public ApiExceptionHandler(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiErrorResponse(
                e.getStatus().name(),
                e.getMessage(),
                MDC.get(RequestIdFilter.MDC_KEY)
        ));
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(
                "BAD_REQUEST",
                e.getMessage(),
                MDC.get(RequestIdFilter.MDC_KEY)
        ));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(
                "VALIDATION_ERROR",
                "Invalid request",
                MDC.get(RequestIdFilter.MDC_KEY)
        ));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, org.hibernate.exception.ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(Exception e) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        String fkDetails = isDevProfile() ? foreignKeyDiagnostics() : "";
        log.error("Data integrity violation requestId={} fk_check={}", requestId, fkDetails, e);
        String message = "Foreign key constraint failed";
        if (fkDetails != null && !fkDetails.isBlank()) {
            message = message + " (" + fkDetails + ")";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
                "FK_CONSTRAINT",
                message,
                requestId
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                "INTERNAL_ERROR",
                "Unexpected error",
                MDC.get(RequestIdFilter.MDC_KEY)
        ));
    }

    private String foreignKeyDiagnostics() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("PRAGMA foreign_key_check");
            if (rows.isEmpty()) return "";
            return rows.stream()
                    .limit(5)
                    .map(this::formatForeignKeyRow)
                    .collect(Collectors.joining("; "));
        } catch (Exception ex) {
            log.warn("foreign_key_check failed", ex);
            return "";
        }
    }

    private boolean isDevProfile() {
        return environment != null && environment.acceptsProfiles(Profiles.of("dev"));
    }

    private String formatForeignKeyRow(Map<String, Object> row) {
        String table = String.valueOf(row.get("table"));
        String parent = String.valueOf(row.get("parent"));
        Object fkId = row.get("fkid");
        String columns = resolveForeignKeyColumns(table, fkId);
        return "table=" + table + " column=" + columns + " parent=" + parent;
    }

    private String resolveForeignKeyColumns(String table, Object fkId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("PRAGMA foreign_key_list(" + table + ")");
            String targetId = String.valueOf(fkId);
            List<String> cols = rows.stream()
                    .filter(r -> targetId.equals(String.valueOf(r.get("id"))))
                    .map(r -> String.valueOf(r.get("from")))
                    .distinct()
                    .toList();
            if (cols.isEmpty()) return "?";
            return String.join(",", cols);
        } catch (Exception ex) {
            log.warn("foreign_key_list failed for table={}", table, ex);
            return "?";
        }
    }
}
