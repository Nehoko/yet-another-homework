package ge.imikhailov.omno.web.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_buildsProblemDetail() {
        ProductNotFoundException ex = new ProductNotFoundException(99L);

        ProblemDetail pd = handler.handleNotFound(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getTitle()).isEqualTo("Product Not Found");
        assertThat(pd.getDetail()).contains("99");
        assertThat(pd.getProperties().get("productId")).isEqualTo(99L);
        assertThat(pd.getProperties().get("timestamp")).isInstanceOf(OffsetDateTime.class);
    }

    @Test
    void handleValidation_collectsFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "fieldA", "msgA"));
        bindingResult.addError(new FieldError("target", "fieldB", null)); // missing message -> default
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail pd = handler.handleValidation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getTitle()).isEqualTo("Constraint Violation");
        assertThat(pd.getDetail()).isEqualTo("Validation failed");
        assertThat(pd.getProperties().get("timestamp")).isInstanceOf(OffsetDateTime.class);
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) pd.getProperties().get("errors");
        assertThat(errors).containsEntry("fieldA", "msgA");
        assertThat(errors).containsEntry("fieldB", "Invalid value");
    }
}
