package com.propfolio.common.error;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 ProblemDetail responses.
 *
 * <pre>
 * 400 { "title": "Invalid request", "detail": "...", "errors": [ { "field": "name", "message": "..." } ] }
 * 404 { "title": "Not found", "detail": "Property not found" }
 * 409 { "title": "Conflict", "detail": "..." }
 * </pre>
 *
 * The base class already covers framework errors (malformed JSON, wrong method, etc.)
 * with ProblemDetail bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String ERRORS_PROPERTY = "errors";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "Some fields need attention.");
        problem.setProperty(ERRORS_PROPERTY, errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalid(InvalidRequestException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
        problem.setProperty(ERRORS_PROPERTY, List.of(new FieldError(ex.getField(), ex.getMessage())));
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        ex.getProperties().forEach(problem::setProperty);
        return problem;
    }

    /**
     * Safety net when a database constraint catches something the service checks missed
     * (e.g. two requests racing). Details are logged, not returned.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Database constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict",
                "This change conflicts with existing data. Refresh and try again.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        return problem;
    }
}
