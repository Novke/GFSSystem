package tri.novica.gfssystem.service.lti;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.lti.LtiRegistracija;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Provera {@code id_token}-a iz LTI 1.3 launch-a (spec 8.3): potpis RS256 ključem iz JWKS-a registracije
 * ({@link NimbusJwtDecoder}), pa validatori: {@code iss} = izdavač registracije, {@code aud} sadrži Client ID, {@code azp}
 * (obavezan kad {@code aud} ima više vrednosti, a kad postoji mora biti Client ID), {@code exp} i {@code iat} obavezni
 * (leeway 60 s, i {@code nbf} kad postoji), {@code nonce} jednak onom iz stanja, {@code deployment_id} registracije,
 * {@code sub} prisutan; zatim {@code message_type=LtiResourceLinkRequest} i {@code version=1.3.0}.
 * <p>
 * JWKS se kešira po URL-u (5 min; nepoznat {@code kid} osvežava keš, najviše dva dohvata u 30 s, pa napadač sa praznim
 * tokenima ne pretvara GFS u generator zahteva ka Moodle-u). Dohvat je ograničen ({@link LtiDohvatKljuceva}: rokovi,
 * veličina, bez preusmerenja, bez internih adresa). Greške nose samo stalne reči ({@link LtiOdbijeno#razlog()}), nikad
 * vrednosti iz tokena.
 */
@Component
public class LtiProvera {

    static final String LTI = "https://purl.imsglobal.org/spec/lti/claim/";
    static final String DEPLOYMENT_ID = LTI + "deployment_id";
    static final String MESSAGE_TYPE = LTI + "message_type";
    static final String VERSION = LTI + "version";
    static final String RESOURCE_LINK_REQUEST = "LtiResourceLinkRequest";
    static final String VERZIJA = "1.3.0";

    static final Duration LEEWAY = Duration.ofSeconds(60);
    static final int NAJVECI_TOKEN = 32 * 1024;
    static final int NAJDUZI_SUB = 255;
    static final int ROK_VEZE_MS = 3_000;
    static final int ROK_CITANJA_MS = 5_000;
    static final int NAJVISE_BAJTOVA = 64 * 1024;
    static final long KES_MS = Duration.ofMinutes(5).toMillis();
    static final long ROK_OSVEZAVANJA_MS = Duration.ofSeconds(15).toMillis();
    static final long NAJCESCE_MS = Duration.ofSeconds(30).toMillis();
    /** Registracija je malo; zaštita od rasta mape kad se JWKS URL-ovi menjaju. */
    static final int NAJVISE_IZVORA = 100;

    private final Clock clock;
    private final boolean testRezim;
    private final Map<String, JWKSource<SecurityContext>> izvori = new ConcurrentHashMap<>();

    public LtiProvera(Clock clock, @Value("${gfs.lti.dozvoli-http:false}") boolean testRezim) {
        this.clock = clock;
        this.testRezim = testRezim;
    }

    /** Proveren skup claim-ova; inače {@link LtiOdbijeno} ({@code neispravan-token} ili {@code nepodrzana-poruka}). */
    public JWTClaimsSet proveri(LtiRegistracija r, String idToken, String ocekivaniNonce) {
        if (idToken == null || idToken.isBlank() || idToken.length() > NAJVECI_TOKEN) {
            throw neispravan("oblik");
        }
        NimbusJwtDecoder dekoder = NimbusJwtDecoder.withJwkSource(izvor(r.getJwksUrl()))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        dekoder.setJwtValidator(validator(r, ocekivaniNonce));
        try {
            dekoder.decode(idToken);
        } catch (JwtValidationException e) {
            // sve pale provere, stalne reči iz pravilo(...)
            String razlog = e.getErrors().stream().map(OAuth2Error::getDescription).sorted()
                    .collect(Collectors.joining(","));
            throw neispravan(razlog.isEmpty() ? "validacija" : razlog);
        } catch (BadJwtException e) {
            throw neispravan("potpis");
        } catch (JwtException e) {
            throw neispravan("kljucevi-platforme");
        }
        JWTClaimsSet c;
        try {
            // potpis je proveren za isti tekst; ovde samo sirove vrednosti claim-ova
            c = SignedJWT.parse(idToken).getJWTClaimsSet();
        } catch (ParseException e) {
            throw neispravan("oblik");
        }
        if (c.getIssueTime() == null) {
            // Spring dopuni iat iz exp kad ga nema (MappedJwtClaimSetConverter), pa se obaveznost proverava na sirovom
            throw neispravan("iat");
        }
        if (!RESOURCE_LINK_REQUEST.equals(c.getClaim(MESSAGE_TYPE))) {
            throw new LtiOdbijeno(LtiGreska.NEPODRZANA_PORUKA, "message_type");
        }
        if (!VERZIJA.equals(c.getClaim(VERSION))) {
            throw new LtiOdbijeno(LtiGreska.NEPODRZANA_PORUKA, "version");
        }
        return c;
    }

    private OAuth2TokenValidator<Jwt> validator(LtiRegistracija r, String ocekivaniNonce) {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                pravilo("iss", j -> r.getIzdavac().equals(bezZavrsneKose(j.getClaimAsString("iss")))),
                pravilo("aud", j -> j.getAudience() != null && j.getAudience().contains(r.getClientId())),
                pravilo("azp", j -> {
                    String azp = j.getClaimAsString("azp");
                    if (azp == null) return j.getAudience().size() == 1;
                    return azp.equals(r.getClientId());
                }),
                pravilo("exp", j -> j.getExpiresAt() != null && sada().isBefore(j.getExpiresAt().plus(LEEWAY))),
                pravilo("iat", j -> j.getIssuedAt() != null && !j.getIssuedAt().minus(LEEWAY).isAfter(sada())),
                pravilo("nbf", j -> j.getNotBefore() == null || !j.getNotBefore().minus(LEEWAY).isAfter(sada())),
                pravilo("nonce", j -> jednako(ocekivaniNonce, j.getClaimAsString("nonce"))),
                pravilo("deployment_id", j -> r.getDeploymentId().equals(j.getClaimAsString(DEPLOYMENT_ID))),
                pravilo("sub", j -> {
                    String sub = j.getSubject();
                    return sub != null && !sub.isBlank() && sub.length() <= NAJDUZI_SUB;
                })));
    }

    /** Validator čija je jedina poruka stalna reč {@code razlog} (bezbedno za log). */
    private static OAuth2TokenValidator<Jwt> pravilo(String razlog, Predicate<Jwt> uslov) {
        OAuth2Error greska = new OAuth2Error("invalid_token", razlog, null);
        return j -> {
            boolean ok;
            try {
                ok = uslov.test(j);
            } catch (RuntimeException e) {
                ok = false;   // claim pogrešnog tipa
            }
            return ok ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(greska);
        };
    }

    private Instant sada() {
        return clock.instant();
    }

    private static boolean jednako(String a, String b) {
        return a != null && b != null
                && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String bezZavrsneKose(String s) {
        if (s == null) return null;
        String u = s;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private JWKSource<SecurityContext> izvor(String jwksUrl) {
        JWKSource<SecurityContext> postoji = izvori.get(jwksUrl);
        if (postoji != null) {
            return postoji;
        }
        if (izvori.size() >= NAJVISE_IZVORA) {
            izvori.clear();
        }
        return izvori.computeIfAbsent(jwksUrl, this::napravi);
    }

    private JWKSource<SecurityContext> napravi(String jwksUrl) {
        URL url;
        try {
            url = URI.create(jwksUrl).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw neispravan("jwks-url");
        }
        return JWKSourceBuilder.<SecurityContext>create(url,
                        new LtiDohvatKljuceva(ROK_VEZE_MS, ROK_CITANJA_MS, NAJVISE_BAJTOVA, testRezim))
                .cache(KES_MS, ROK_OSVEZAVANJA_MS)
                .refreshAheadCache(false)
                .rateLimited(NAJCESCE_MS)
                .retrying(false)
                .outageTolerant(false)
                .build();
    }

    private static LtiOdbijeno neispravan(String razlog) {
        return new LtiOdbijeno(LtiGreska.NEISPRAVAN_TOKEN, razlog);
    }
}
