package bo.edu.caseapp.uml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import bo.edu.caseapp.error.ConflictException;
import bo.edu.caseapp.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void persistenceCollisionIsReturnedAsConflictWithoutDatabaseDetails() {
    var response = handler.handleConflict(new ConflictException(
        "El identificador del elemento ya está en uso. Reintenta la operación."));

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertFalse(response.getBody().message().contains("database"));
  }

  @Test
  void unexpectedErrorsDoNotExposeInternalMessages() {
    var response = handler.handleUnexpected(new RuntimeException("internal database detail"),
        mock(HttpServletRequest.class));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("Ocurrió un error inesperado.", response.getBody().message());
  }
}
