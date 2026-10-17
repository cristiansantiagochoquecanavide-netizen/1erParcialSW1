package bo.edu.caseapp.uml.collab;

import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class UmlWebSocketHandlerTest {
  @Test
  void broadcastsOnlyToSessionsRegisteredForTheSameDiagramAndRemovesClosedSessions() throws Exception {
    UmlWebSocketHandler handler = new UmlWebSocketHandler();
    UUID sharedDiagram = UUID.randomUUID();
    UUID otherDiagram = UUID.randomUUID();
    WebSocketSession userA = session(sharedDiagram);
    WebSocketSession userB = session(sharedDiagram);
    WebSocketSession otherProject = session(otherDiagram);

    handler.afterConnectionEstablished(userA);
    handler.afterConnectionEstablished(userB);
    handler.afterConnectionEstablished(otherProject);
    handler.broadcastDiagramChanged(sharedDiagram, "ELEMENT_SAVED");

    verify(userA).sendMessage(any(TextMessage.class));
    verify(userB).sendMessage(any(TextMessage.class));
    verify(otherProject, never()).sendMessage(any(TextMessage.class));

    handler.afterConnectionClosed(userA, null);
    handler.broadcastDiagramChanged(sharedDiagram, "ELEMENT_SAVED");

    verify(userA, times(1)).sendMessage(any(TextMessage.class));
    verify(userB, times(2)).sendMessage(any(TextMessage.class));
  }

  private WebSocketSession session(UUID diagramId) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/diagrams/" + diagramId));
    when(session.isOpen()).thenReturn(true);
    return session;
  }
}
