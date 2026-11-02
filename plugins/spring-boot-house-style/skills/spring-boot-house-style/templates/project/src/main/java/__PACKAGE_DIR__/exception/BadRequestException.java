package __PACKAGE__.exception;

import lombok.Getter;

@Getter
public class BadRequestException extends RuntimeException {

    private final String details;

    public BadRequestException(String details) {
        super(details);
        this.details = details;
    }
}
