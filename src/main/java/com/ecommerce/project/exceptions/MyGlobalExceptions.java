package com.ecommerce.project.exceptions;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.ecommerce.project.payload.APIResponse;
import jdk.dynalink.linker.MethodHandleTransformer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class MyGlobalExceptions {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> myMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String , String> response = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(err -> {
            String fieldName = ((FieldError)err).getField();
            String message = err.getDefaultMessage();
            response.put(fieldName, message);
        });
        return new ResponseEntity<Map<String , String >>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<APIResponse> myResourceNotFoundException(ResourceNotFoundException e) {
        String message = e.getMessage();
        APIResponse apiResponse = new APIResponse(message, false);
        return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<APIResponse> myAPIException(APIException e) {
        String message = e.getMessage();
        APIResponse apiResponse = new APIResponse(message, false);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Hibernate validates entities on persist/update as well. Without this
     * handler that failure escapes to the /error page and the client sees a
     * generic 500 (or a 401 before /error was permitted), instead of a clear
     * message about which field is wrong.
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> myConstraintViolationException(
            jakarta.validation.ConstraintViolationException e) {
        return new ResponseEntity<>(violationMessages(e), HttpStatus.BAD_REQUEST);
    }

    /**
     * Hibernate validates on flush, and that failure is wrapped in a
     * TransactionSystemException ("Could not commit JPA transaction"). Unwrap it
     * so the client still gets "description: Product description must contain
     * atleast 6 characters" instead of a generic 500.
     */
    @ExceptionHandler(org.springframework.transaction.TransactionSystemException.class)
    public ResponseEntity<Map<String, String>> myTransactionSystemException(
            org.springframework.transaction.TransactionSystemException e) {
        Throwable cause = e.getCause();
        while (cause != null && !(cause instanceof jakarta.validation.ConstraintViolationException)) {
            cause = cause.getCause();
        }
        jakarta.validation.ConstraintViolationException violation =
                (jakarta.validation.ConstraintViolationException) cause;
        if (violation == null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Product save nahi ho saka. Data check karein.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(violationMessages(violation), HttpStatus.BAD_REQUEST);
    }

    private Map<String, String> violationMessages(jakarta.validation.ConstraintViolationException e) {
        Map<String, String> response = new HashMap<>();
        e.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath() != null
                    ? violation.getPropertyPath().toString()
                    : "error";
            response.put(field, violation.getMessage());
        });
        if (response.isEmpty()) {
            response.put("error", "Validation failed");
        }
        return response;
    }
}

// Here Making Error message more user friendly
// getDefaultMessage(); is => must not be blank
