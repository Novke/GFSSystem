package tri.novica.gfssystem.rest.lti;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tri.novica.gfssystem.service.lti.LtiKljucServis;

import java.time.Duration;
import java.util.Map;

/**
 * Javni ključevi alata (JWKS) za Moodle; ANONIMNO ({@code PristupMatrica}). Izlaz je samo javni deo
 * ({@code JWKSet.toJSONObject()} podrazumevano izostavlja privatne delove).
 */
@RestController
@RequestMapping("/public/lti")
@RequiredArgsConstructor
public class LtiJwksRest {

    private final LtiKljucServis kljucevi;

    @GetMapping("/jwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(300)).cachePublic())
                .contentType(MediaType.APPLICATION_JSON)
                .body(kljucevi.javni().toJSONObject());
    }
}
