package bo.edu.caseapp.uml.collab;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class UmlWebSocketConfig implements WebSocketConfigurer {
  private final UmlWebSocketHandler handler;

  public UmlWebSocketConfig(UmlWebSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/diagrams/{diagramId}")
        .setAllowedOriginPatterns("*");
  }
}
