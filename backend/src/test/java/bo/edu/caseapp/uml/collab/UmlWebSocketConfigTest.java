package bo.edu.caseapp.uml.collab;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class UmlWebSocketConfigTest {
  @Test
  void requiresExplicitOriginsInsteadOfWildcard() {
    assertThrows(IllegalArgumentException.class, () -> new UmlWebSocketConfig(
        mock(UmlWebSocketHandler.class), mock(JwtWebSocketHandshakeInterceptor.class), "*"));
  }

  @Test
  void acceptsConfiguredOriginAllowlist() {
    assertDoesNotThrow(() -> new UmlWebSocketConfig(
        mock(UmlWebSocketHandler.class), mock(JwtWebSocketHandshakeInterceptor.class),
        "http://localhost:5173,https://case.example.edu"));
  }
}
