package com.diyncrafts.web.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS for transcoding progress ({@code /topic/progress-{taskId}}).
 * <p>
 * With {@code app.websocket.relay.enabled=true} messages go through RabbitMQ's STOMP plugin so that
 * several application instances can publish to the same clients; otherwise an in-memory broker is
 * used (single instance, local development, tests).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final boolean relayEnabled;
    private final String relayHost;
    private final int relayPort;
    private final String relayLogin;
    private final String relayPasscode;

    public WebSocketConfig(CorsProperties corsProperties,
            @Value("${app.websocket.relay.enabled:false}") boolean relayEnabled,
            @Value("${app.websocket.relay.host:localhost}") String relayHost,
            @Value("${app.websocket.relay.port:61613}") int relayPort,
            @Value("${app.websocket.relay.login:${spring.rabbitmq.username:guest}}") String relayLogin,
            @Value("${app.websocket.relay.passcode:${spring.rabbitmq.password:guest}}") String relayPasscode) {
        this.corsProperties = corsProperties;
        this.relayEnabled = relayEnabled;
        this.relayHost = relayHost;
        this.relayPort = relayPort;
        this.relayLogin = relayLogin;
        this.relayPasscode = relayPasscode;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        if (relayEnabled) {
            registry.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(relayHost)
                    .setRelayPort(relayPort)
                    .setClientLogin(relayLogin)
                    .setClientPasscode(relayPasscode)
                    .setSystemLogin(relayLogin)
                    .setSystemPasscode(relayPasscode);
        } else {
            registry.enableSimpleBroker("/topic", "/queue");
        }
    }
}
