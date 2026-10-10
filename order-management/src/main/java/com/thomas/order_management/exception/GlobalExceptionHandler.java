package com.thomas.order_management.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liefert alle Fehler als RFC-7807-ProblemDetail. Fachliche Fehler tragen zusätzlich
 * eine SAP-artige Nachrichten-ID ("messageId"), die das Frontend in der Meldungsliste anzeigt.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        ProblemDetail problem = problem(ex.getStatus(), "Fachlicher Fehler", ex.getMessage());
        problem.setProperty("messageId", ex.getMessageId());
        problem.setProperty("severity", "E");
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Nicht gefunden", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Konflikt", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Beleg wurde zwischenzeitlich geändert",
                "Der Beleg wurde von einem anderen Benutzer geändert. Bitte neu laden.");
        problem.setProperty("messageId", "MC 601");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Ungültige Eingabe",
                "Bitte prüfen Sie die markierten Felder.");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Ungültige Eingabe", ex.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleUnreadable(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Ungültige Anfrage", "Die Anfrage konnte nicht gelesen werden.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Nicht gefunden",
                "Der angeforderte Endpunkt wurde nicht gefunden: " + ex.getResourcePath());
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleGenericRuntimeException(RuntimeException ex) {
        logger.error("Ein unerwarteter interner Fehler ist aufgetreten", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Interner Fehler", "Ein interner Fehler ist aufgetreten.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
