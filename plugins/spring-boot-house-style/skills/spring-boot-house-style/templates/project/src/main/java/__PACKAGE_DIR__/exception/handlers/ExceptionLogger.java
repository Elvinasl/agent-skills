package __PACKAGE__.exception.handlers;

import __PACKAGE__.model.ErrorResponse;
// >>>mod:sentry
import io.sentry.Sentry;
// <<<mod:sentry
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExceptionLogger {

    public void expected(HttpServletRequest req, Exception e, HttpStatus status) {
        log.warn("HTTP {} {} -> {} {}: {}", req.getMethod(), req.getRequestURI(), status.value(),
                e.getClass().getSimpleName(), e.getMessage());
    }

    public void unexpected(HttpServletRequest req, Exception e, HttpStatus status, ErrorResponse body) {
        log.error("HTTP {} {} -> {} {}: {}", req.getMethod(), req.getRequestURI(), status.value(),
                e.getClass().getSimpleName(), e.getMessage(), e);
        // >>>mod:sentry
        Sentry.captureException(e, scope -> {
            if (body.errorId() != null) {
                scope.setTag("errorId", body.errorId().name());
            }
            scope.setTag("httpStatus", String.valueOf(status.value()));
        });
        // <<<mod:sentry
    }
}
