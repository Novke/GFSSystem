package tri.novica.gfssystem.config;

import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;
import tri.novica.gfssystem.rest.uzivo.PublicUzivoRest;
import tri.novica.gfssystem.service.uzivo.UcesnikService;

import java.util.Map;
import java.util.Optional;

/**
 * Uloga sesije po ulazu. {@code /public/ws} (javno, nginx {@code /api/public/ws}) je student: traži važeći kolačić
 * {@code gfs_uzivo} (učesnik postoji, nije izbačen, izvođenje AKTIVNO), inače 403 i rukovanje ne uspeva (telefon tada
 * nudi unos imena). {@code /ws} (nginx {@code /api/ws}, iza basic-auth-a) je nastavnik. Origin proverava Spring
 * ({@code setAllowedOriginPatterns} iz {@code gfs.front.url}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UzivoHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATR_ULOGA = "uloga";
    public static final String ATR_IZVODJENJE = "izvodjenjeId";
    public static final String ATR_UCESNIK = "ucesnikId";
    static final String STUDENTSKI_ULAZ = "/public/ws";

    private final UcesnikService ucesnikService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String putanja = request.getURI().getPath();
        if (putanja == null || !putanja.endsWith(STUDENTSKI_ULAZ)) {
            attributes.put(ATR_ULOGA, Uloga.NASTAVNIK);
            return true;
        }
        Optional<Ucesnik> ucesnik = kolacic(request).flatMap(ucesnikService::poTokenu);
        if (ucesnik.isEmpty()) {
            log.debug("Rukovanje odbijeno: nema važećeg kolačića");
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        Ucesnik u = ucesnik.get();
        attributes.put(ATR_ULOGA, Uloga.STUDENT);
        attributes.put(ATR_UCESNIK, u.getId());
        attributes.put(ATR_IZVODJENJE, u.getIzvodjenje().getId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // ništa
    }

    private static Optional<String> kolacic(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servlet)) return Optional.empty();
        Cookie[] kolacici = servlet.getServletRequest().getCookies();
        if (kolacici == null) return Optional.empty();
        for (Cookie c : kolacici) {
            if (PublicUzivoRest.KOLACIC.equals(c.getName())) return Optional.ofNullable(c.getValue());
        }
        return Optional.empty();
    }
}
