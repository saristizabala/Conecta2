package com.example.worker_registry.Exceptions;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

import java.util.*;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ======= Helpers comunes (se conserva tu formato de salida) =======
    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String mensaje, List<String> errores) {
        return build(status, mensaje, errores, null);
    }

    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String mensaje, List<String> errores, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("mensaje", mensaje);
        if (errores != null && !errores.isEmpty()) {
            body.put("errores", errores);
        }
        if (path != null) {
            body.put("path", path);
        }
        return ResponseEntity.status(status).body(body);
    }

    private String reqPath(HttpServletRequest req) {
        return (req != null) ? req.getRequestURI() : null;
    }

    // ==========================================================
    // Validaciones de @Valid (cuerpo JSON)
    // ==========================================================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> errores = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Errores de validación encontrados", errores, reqPath(req));
    }

    // Validación en parámetros (path/query)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {
        List<String> errores = ex.getConstraintViolations()
                .stream()
                .map(this::formatConstraintViolation)
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Errores de validación encontrados", errores, reqPath(req));
    }

    private String formatConstraintViolation(ConstraintViolation<?> cv) {
        String field = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "param";
        return field + ": " + cv.getMessage();
    }

    // Tipos inválidos en path variables / query params
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = String.format("Parámetro '%s' con valor '%s' no es del tipo esperado",
                ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST, "Petición inválida", List.of(msg), reqPath(req));
    }

    // Falta un parámetro requerido
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        String msg = String.format("Falta el parámetro requerido '%s'", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "Petición inválida", List.of(msg), reqPath(req));
    }

    // JSON mal formado o con tipos incompatibles
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        String detalle = Optional.ofNullable(ex.getMostSpecificCause())
                .map(Throwable::getMessage)
                .orElse(ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Cuerpo de la solicitud no legible o con formato inválido",
                List.of(detalle), reqPath(req));
    }

    // ==========================================================
    // Seguridad / JWT
    // ==========================================================

    // 403 - Acceso prohibido
    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<Map<String, Object>> handleForbidden(Exception ex, HttpServletRequest req) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Acceso prohibido";
        return build(HttpStatus.FORBIDDEN, msg, null, reqPath(req));
    }

    // 401 - No autorizado (falta token, token inválido)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "No autorizado";
        return build(HttpStatus.UNAUTHORIZED, msg, null, reqPath(req));
    }

    // 401 - JWT expirado
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Map<String, Object>> handleExpiredJwt(ExpiredJwtException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Token expirado. Por favor inicia sesión nuevamente.",
                null, reqPath(req));
    }

    // 401 - JWT inválido / mal formado / firma inválida / no soportado
    @ExceptionHandler({
            MalformedJwtException.class,
            SignatureException.class,
            UnsupportedJwtException.class,
            JwtException.class
    })
    public ResponseEntity<Map<String, Object>> handleJwtGeneric(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Token inválido. Por favor inicia sesión nuevamente.",
                List.of(ex.getMessage()), reqPath(req));
    }

    // ==========================================================
    // Negocio / Repositorio / Genéricos
    // ==========================================================

    // 404 común cuando no se encuentra un recurso (servicio, cliente, etc.)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND,
                ex.getMessage() != null ? ex.getMessage() : "Recurso no encontrado",
                null, reqPath(req));
    }

    // 400 para reglas de negocio inválidas (fecha anterior, estado no permitido, etc.)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "Petición inválida",
                null, reqPath(req));
    }

    // 409 conflictos por llaves duplicadas/uniqueness (correo, celular, etc.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException ex, HttpServletRequest req) {
        String detalle = Optional.ofNullable(ex.getMostSpecificCause())
                .map(Throwable::getMessage)
                .orElse(ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflicto de datos (posible duplicado/relación inválida)",
                List.of(detalle), reqPath(req));
    }

    // Genérico 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno. Intenta de nuevo o contacta soporte.",
                List.of(ex.getMessage()), reqPath(req));
    }
}
