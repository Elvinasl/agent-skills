package __PACKAGE__.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void validationResponseCarriesFieldViolations() {
        var response = ErrorResponse.validation("invalid", List.of(new ErrorResponse.FieldViolation("email", "must not be blank")));

        assertThat(response.errorId()).isEqualTo(ErrorId.VALIDATION_FAILED);
        assertThat(response.validationErrors()).extracting(ErrorResponse.FieldViolation::field).containsExactly("email");
        assertThat(response.timestamp()).isNotNull();
    }
}
