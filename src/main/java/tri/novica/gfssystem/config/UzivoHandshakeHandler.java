package tri.novica.gfssystem.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/** Korisnik sesije iz atributa koje je upisao {@link UzivoHandshakeInterceptor}: student {@code u-<id>}, nastavnik {@code n-<uuid>}. */
@Component
public class UzivoHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        if (attributes.get(UzivoHandshakeInterceptor.ATR_ULOGA) == Uloga.STUDENT
                && attributes.get(UzivoHandshakeInterceptor.ATR_UCESNIK) instanceof Long ucesnikId) {
            return new StompPrincipal(StompPrincipal.student(ucesnikId));
        }
        return new StompPrincipal("n-" + UUID.randomUUID());
    }
}
