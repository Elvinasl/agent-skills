package __PACKAGE__.exception.handlers;

import __PACKAGE__.exception.BadRequestException;
import __PACKAGE__.i18n.Translator;
import __PACKAGE__.model.ErrorId;
import __PACKAGE__.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class CommonExceptionHandlers {

    private final Translator translator;
    private final ExceptionLogger exceptionLogger;

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handle(MethodArgumentNotValidException e, HttpServletRequest req) {
        var violations = e.getBindingResult().getFieldErrors().stream()
                .map(it -> new ErrorResponse.FieldViolation(it.getField(), it.getDefaultMessage()))
                .toList();
        exceptionLogger.expected(req, e, HttpStatus.BAD_REQUEST);
        return ErrorResponse.validation(translator.translate("general.err.validation"), violations);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResponse handle(HttpMessageNotReadableException e, HttpServletRequest req) {
        exceptionLogger.expected(req, e, HttpStatus.BAD_REQUEST);
        return ErrorResponse.of(translator.translate("general.err.bad_request", "malformed request body"), ErrorId.BAD_REQUEST);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BadRequestException.class)
    public ErrorResponse handle(BadRequestException e, HttpServletRequest req) {
        exceptionLogger.expected(req, e, HttpStatus.BAD_REQUEST);
        return ErrorResponse.of(translator.translate("general.err.bad_request", e.getDetails()), ErrorId.BAD_REQUEST);
    }
}
