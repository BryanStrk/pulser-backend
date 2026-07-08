package com.bryanstrk.pulser.config;

import com.bryanstrk.pulser.shared.websocket.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Infraestructura STOMP del dashboard de check-in en vivo.
 * - Endpoint de handshake: /ws (permitAll en SecurityConfig; la autenticacion real ocurre en el
 *   CONNECT via StompAuthChannelInterceptor, no en el upgrade HTTP).
 * - Broker simple en memoria sobre /topic (destinos server->client, p. ej.
 *   /topic/eventos/{id}/checkins).
 * - Interceptor de canal inbound: autentica el CONNECT y autoriza el SUBSCRIBE.
 * Los origenes permitidos del WS reutilizan la MISMA propiedad cors.allowed-origins que el REST.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final List<String> allowedOrigins;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(
            @Value("${cors.allowed-origins}") String allowedOrigins,
            StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Interceptor sobre el canal inbound -> el Principal fijado en el CONNECT esta disponible
        // cuando llega el SUBSCRIBE de la misma sesion.
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
