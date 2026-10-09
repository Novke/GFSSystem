package tri.novica.gfssystem.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;

/**
 * STOMP preko čistog WebSocket-a (bez SockJS), spec 4.3. Dva ulaza: {@code /ws} nastavnik (iza basic-auth-a) i
 * {@code /public/ws} student (javno, samo sa kolačićem). Origin iz {@code gfs.front.url} (isti spisak kao REST CORS).
 * Broker u memoriji ({@code /topic}, {@code /queue}), heartbeat 10 s / 10 s. Snimci stanja jednoj sesiji stižu redom
 * kojim su poslati ({@code setPreservePublishOrder}). Dolazne poruke namerno nisu uređene
 * ({@code setPreserveReceiveOrder}): Spring tada izuzetak iz {@code UzivoChannelInterceptor}-a samo loguje, bez ERROR
 * frame-a i bez zatvaranja veze.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final int MAX_PORUKA = 8 * 1024;
    static final int MAX_BAFER_SLANJA = 512 * 1024;
    static final int MAX_VREME_SLANJA_MS = 15_000;
    static final long HEARTBEAT_MS = 10_000;

    private final UzivoHandshakeInterceptor handshakeInterceptor;
    private final UzivoHandshakeHandler handshakeHandler;
    private final UzivoChannelInterceptor channelInterceptor;
    private final TaskScheduler brokerScheduler;
    private final String frontUrl;

    public WebSocketConfig(UzivoHandshakeInterceptor handshakeInterceptor, UzivoHandshakeHandler handshakeHandler,
                           UzivoChannelInterceptor channelInterceptor,
                           @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler brokerScheduler,
                           @Value("${gfs.front.url}") String frontUrl) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.channelInterceptor = channelInterceptor;
        this.brokerScheduler = brokerScheduler;
        this.frontUrl = frontUrl;
    }

    /** Origini iz {@code gfs.front.url} (zarez, razmaci se odsecaju). */
    static String[] origini(String frontUrl) {
        return Arrays.stream(frontUrl.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws", "/public/ws")
                .setAllowedOriginPatterns(origini(frontUrl))
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(brokerScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_PORUKA)
                .setSendBufferSizeLimit(MAX_BAFER_SLANJA)
                .setSendTimeLimit(MAX_VREME_SLANJA_MS);
    }
}
