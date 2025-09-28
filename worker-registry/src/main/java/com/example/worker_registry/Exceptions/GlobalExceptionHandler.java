package com.example.worker_registry.Exceptions;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> handleValidationExceptions(ConstraintViolationException ex) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", HttpStatus.BAD_REQUEST.value());
    response.put("mensaje", "Errores de validación encontrados");
    response.put("errores", ex.getConstraintViolations()
      .stream()
      .map(v -> v.getPropertyPath() + ": " + v.getMessage())
      .collect(Collectors.toList()));
    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleBodyValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors()
      .stream()
      .collect(Collectors.toMap(
        fe -> fe.getField(),
        fe -> fe.getDefaultMessage() == null ? "Inválido" : fe.getDefaultMessage(),
        (a, b) -> a
      ));
    Map<String, Object> body = new HashMap<>();
    body.put("status", 400);
    body.put("mensaje", "Errores de validación en el cuerpo de la solicitud");
    body.put("errores", fieldErrors);
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
  }
}
