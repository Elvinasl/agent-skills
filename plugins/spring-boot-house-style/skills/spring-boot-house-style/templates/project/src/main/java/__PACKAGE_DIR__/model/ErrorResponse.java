package __PACKAGE__.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        String message,
        ErrorId errorId,
        Object errorData,
        List<FieldViolation> validationErrors
) {

    public record FieldViolation(String field, String message) {
    }

    public static ErrorResponse of(String message, ErrorId errorId) {
        return new ErrorResponse(Instant.now(), message, errorId, null, null);
    }

    public static ErrorResponse of(String message, ErrorId errorId, Object errorData) {
        return new ErrorResponse(Instant.now(), message, errorId, errorData, null);
    }

    public static ErrorResponse validation(String message, List<FieldViolation> violations) {
        return new ErrorResponse(Instant.now(), message, ErrorId.VALIDATION_FAILED, null, violations);
    }
}
