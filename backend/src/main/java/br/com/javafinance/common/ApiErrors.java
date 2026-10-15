package br.com.javafinance.common;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String message, Map<String, String> fields) {}
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> domain(ApiException ex) { return ResponseEntity.status(ex.status).body(new ErrorBody(ex.getMessage(), Map.of())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
        Map<String,String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorBody("Revise os campos indicados.", fields));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> malformed(Exception ex) { return ResponseEntity.badRequest().body(new ErrorBody("Dados inválidos. Confira datas, valores e campos.", Map.of())); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> integrity(Exception ex) { return ResponseEntity.status(409).body(new ErrorBody("Registro já existente ou operação conflitante. Atualize a página.", Map.of())); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorBody> concurrent(Exception ex) { return ResponseEntity.status(409).body(new ErrorBody("O registro mudou em outra operação. Atualize e tente novamente.", Map.of())); }
}

