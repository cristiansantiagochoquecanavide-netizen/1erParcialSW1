package bo.edu.caseapp.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(NotFoundException exception) { return response(HttpStatus.NOT_FOUND, exception.getMessage()); }
  @ExceptionHandler({ForbiddenException.class, org.springframework.security.access.AccessDeniedException.class})
  public ResponseEntity<ApiError> handleForbidden(Exception exception) { return response(HttpStatus.FORBIDDEN, exception.getMessage()); }
  @ExceptionHandler({BadRequestException.class, MethodArgumentNotValidException.class})
  public ResponseEntity<ApiError> handleBadRequest(Exception exception) { return response(HttpStatus.BAD_REQUEST, exception.getMessage()); }
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error("Unexpected error on request {}: ", request.getRequestURI(), exception);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado: " + exception.getMessage());
  }
  private ResponseEntity<ApiError> response(HttpStatus status, String message) { return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message)); }
}
