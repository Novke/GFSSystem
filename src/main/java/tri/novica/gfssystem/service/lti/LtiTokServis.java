package tri.novica.gfssystem.service.lti;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.lti.LtiPrijavaCmd;
import tri.novica.gfssystem.entity.lti.LtiRegistracija;
import tri.novica.gfssystem.entity.lti.LtiStanje;
import tri.novica.gfssystem.repository.lti.LtiRegistracijaRepository;
import tri.novica.gfssystem.repository.lti.LtiStanjeRepository;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.KlijentIp;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LTI 1.3 tok (spec 8.3): OIDC login initiation ({@link #prijava}) i launch ({@link #start}).
 * <ul>
 *     <li><b>Prijava:</b> registracija po ({@code iss}, {@code client_id}, {@code lti_deployment_id}), mora biti aktivna.
 *     U {@code lti_stanja} ide heš {@code state}-a, {@code nonce} i heš kolačića {@code __Host-gfs_lti} (10 min); kolačić je
 *     {@code SameSite=None; Secure}, jer {@code form_post} sa Moodle-a je cross-site. Isti pregledač zadržava postojeću
 *     vrednost kolačića, pa dve kartice (dva ulaza odjednom) ne kvare jedna drugoj launch.</li>
 *     <li><b>Launch:</b> stanje se troši atomski pre svake druge provere (jednokratno i kad provera padne), pa kolačić
 *     ovog pregledača, pa token ({@link LtiProvera}). Ishod po ulozi: nastavnik dobija samo poruku (bez sesije), student
 *     sa vezom {@code gfs_student}, student bez veze povezivanje ({@code __Host-gfs_lti_par}).</li>
 * </ul>
 * JWKS se dohvata van transakcije (mreža ne drži vezu iz pula). Preusmerenja na front su relativna; greške su kodovi iz
 * {@link LtiGreska}, detalj samo u logu, bez ijedne vrednosti iz tokena ili zahteva (samo id-jevi, stalne reči, IP).
 */
@Service
@Slf4j
public class LtiTokServis {

    /** Trajanje stanja i Max-Age kolačića {@code __Host-gfs_lti}. */
    public static final Duration TRAJANJE_STANJA = Duration.ofMinutes(10);
    public static final String NALOG = "/nalog";
    public static final String POVEZIVANJE = "/nalog/moodle";
    public static final String NASTAVNIK = "/nalog/moodle?uloga=nastavnik";

    static final int NAJDUZI_ID = 255;
    static final int NAJDUZI_LOGIN_HINT = 2_048;
    static final int NAJDUZI_MESSAGE_HINT = 4_096;
    static final int NAJDUZE_IME = 100;
    /** Vrste znakova koje ime sa Moodle-a ne sme imati: kontrolni, format (smer pisanja, nulte širine), razdvajači redova... */
    private static final Set<Integer> NEVIDLJIVO = Set.of((int) Character.CONTROL, (int) Character.FORMAT,
            (int) Character.LINE_SEPARATOR, (int) Character.PARAGRAPH_SEPARATOR, (int) Character.PRIVATE_USE,
            (int) Character.SURROGATE, (int) Character.UNASSIGNED);
    static final String ULOGE = LtiProvera.LTI + "roles";

    private final LtiRegistracijaRepository registracije;
    private final LtiStanjeRepository stanja;
    private final LtiRegistracijaServis registracijaServis;
    private final LtiProvera provera;
    private final LtiPovezivanjeServis povezivanja;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;
    private final TransactionTemplate transakcija;

    public LtiTokServis(LtiRegistracijaRepository registracije, LtiStanjeRepository stanja,
                        LtiRegistracijaServis registracijaServis, LtiProvera provera,
                        LtiPovezivanjeServis povezivanja, TokenGenerator tokenGenerator, Clock clock,
                        PlatformTransactionManager tm) {
        this.registracije = registracije;
        this.stanja = stanja;
        this.registracijaServis = registracijaServis;
        this.provera = provera;
        this.povezivanja = povezivanja;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
        this.transakcija = new TransactionTemplate(tm);
    }

    /** Login initiation: URL za 302 (auth URL platforme sa parametrima, ili relativna stranica greške). */
    public String prijava(LtiPrijavaCmd cmd, HttpServletRequest req, HttpServletResponse res) {
        String ip = KlijentIp.iz(req);
        try {
            return transakcija.execute(st -> zapocni(cmd, req, res, ip));
        } catch (LtiOdbijeno e) {
            log.info("LTI prijava odbijena: greska={}, razlog={}, ip={}", e.greska().kod(), e.razlog(), ip);
            return e.greska().location();
        }
    }

    private String zapocni(LtiPrijavaCmd cmd, HttpServletRequest req, HttpServletResponse res, String ip) {
        String iss = bezZavrsneKose(obrezi(cmd.iss()));
        String clientId = obrezi(cmd.clientId());
        String deploymentId = obrezi(cmd.ltiDeploymentId());
        if (prazno(iss) || prazno(clientId) || prazno(deploymentId) || iss.length() > NAJDUZI_ID
                || clientId.length() > NAJDUZI_ID || deploymentId.length() > NAJDUZI_ID) {
            throw new LtiOdbijeno(LtiGreska.NEPOZNATA_REGISTRACIJA, "parametri");
        }
        LtiRegistracija r = registracije.findByIzdavacAndClientIdAndDeploymentId(iss, clientId, deploymentId)
                .orElseThrow(() -> new LtiOdbijeno(LtiGreska.NEPOZNATA_REGISTRACIJA, "registracija"));
        if (!r.isAktivna()) {
            throw new LtiOdbijeno(LtiGreska.NEPOZNATA_REGISTRACIJA, "registracija-iskljucena");
        }
        if (prazno(cmd.loginHint()) || cmd.loginHint().length() > NAJDUZI_LOGIN_HINT) {
            throw new LtiOdbijeno(LtiGreska.NEPODRZANA_PORUKA, "login_hint");
        }
        if (cmd.ltiMessageHint() != null && cmd.ltiMessageHint().length() > NAJDUZI_MESSAGE_HINT) {
            throw new LtiOdbijeno(LtiGreska.NEPODRZANA_PORUKA, "lti_message_hint");
        }

        String state = tokenGenerator.novi();
        String nonce = tokenGenerator.novi();
        String kolacic = postojeciKolacic(req).orElseGet(tokenGenerator::novi);
        LocalDateTime sada = LocalDateTime.now(clock);
        LtiStanje s = new LtiStanje();
        s.setStanjeHes(TokenHash.od(state));
        s.setNonce(nonce);
        s.setKolacicHes(TokenHash.od(kolacic));
        s.setRegistracija(r);
        s.setKreirano(sada);
        s.setIstice(sada.plus(TRAJANJE_STANJA));
        stanja.save(s);
        Kolacici.postavi(req, res, Kolacici.LTI, kolacic, TRAJANJE_STANJA, "None");
        log.info("LTI prijava (login initiation), registracija={}, stanje={}, ip={}", r.getId(), s.getId(), ip);

        StringBuilder u = new StringBuilder(r.getAuthUrl()).append(r.getAuthUrl().contains("?") ? '&' : '?');
        parametar(u, "scope", "openid");
        parametar(u, "response_type", "id_token");
        parametar(u, "response_mode", "form_post");
        parametar(u, "prompt", "none");
        parametar(u, "client_id", r.getClientId());
        parametar(u, "redirect_uri", registracijaServis.vrednosti().redirectionUri());
        parametar(u, "login_hint", cmd.loginHint());
        if (!prazno(cmd.ltiMessageHint())) {
            parametar(u, "lti_message_hint", cmd.ltiMessageHint());
        }
        parametar(u, "state", state);
        u.append("nonce=").append(enc(nonce));
        return u.toString();
    }

    /**
     * Launch ({@code form_post} sa platforme): relativno preusmerenje na front. Svaka greška je kod u
     * {@code /nalog/moodle?greska=...} (nikad 500 zbog lošeg zahteva i nikad prijava bez veze).
     */
    public String start(String idToken, String state, HttpServletRequest req, HttpServletResponse res) {
        String ip = KlijentIp.iz(req);
        Long registracijaId = null;
        try {
            LtiStanje s = potrosi(state);
            LtiRegistracija r = s.getRegistracija();
            registracijaId = r.getId();
            proveriKolacic(req, s);
            if (!r.isAktivna()) {
                throw new LtiOdbijeno(LtiGreska.NEPOZNATA_REGISTRACIJA, "registracija-iskljucena");
            }
            JWTClaimsSet c = provera.proveri(r, idToken, s.getNonce());
            List<String> uloge = uloge(c);
            if (uloge.stream().anyMatch(LtiTokServis::jeNastavnik)) {
                log.info("LTI launch: nastavnik, registracija={}, ip={}", r.getId(), ip);
                return NASTAVNIK;
            }
            if (uloge.stream().noneMatch(LtiTokServis::jeStudent)) {
                throw new LtiOdbijeno(LtiGreska.NEPODRZANA_PORUKA, "uloga");
            }
            String sub = c.getSubject();
            if (povezivanja.prijaviPovezanog(r.getIzdavac(), sub, req, res).isPresent()) {
                log.info("LTI launch: student prijavljen, registracija={}, ip={}", r.getId(), ip);
                return NALOG;
            }
            povezivanja.zapocni(r.getId(), sub, ime(c), req, res);
            log.info("LTI launch: student bez veze, povezivanje, registracija={}, ip={}", r.getId(), ip);
            return POVEZIVANJE;
        } catch (LtiOdbijeno e) {
            log.info("LTI launch odbijen: greska={}, razlog={}, registracija={}, ip={}", e.greska().kod(), e.razlog(),
                    registracijaId, ip);
            return e.greska().location();
        }
    }

    /** Nađe i potroši stanje u jednoj kratkoj transakciji (uslovni UPDATE: drugi istovremeni launch dobija 0 redova). */
    private LtiStanje potrosi(String state) {
        if (state == null || !TokenGenerator.FORMAT.matcher(state).matches()) {
            throw new LtiOdbijeno(LtiGreska.ISTEKLA_SESIJA, "state-oblik");
        }
        return transakcija.execute(st -> {
            LtiStanje s = stanja.nadjiSaRegistracijom(TokenHash.od(state))
                    .orElseThrow(() -> new LtiOdbijeno(LtiGreska.ISTEKLA_SESIJA, "state-nepoznat"));
            LocalDateTime sada = LocalDateTime.now(clock);
            if (s.getIskorisceno() != null) {
                throw new LtiOdbijeno(LtiGreska.ISTEKLA_SESIJA, "state-iskoriscen");
            }
            if (!s.getIstice().isAfter(sada)) {
                throw new LtiOdbijeno(LtiGreska.ISTEKLA_SESIJA, "state-istekao");
            }
            if (stanja.potrosi(s.getId(), sada) != 1) {
                throw new LtiOdbijeno(LtiGreska.ISTEKLA_SESIJA, "state-iskoriscen");
            }
            return s;
        });
    }

    /**
     * Kolačić {@code __Host-gfs_lti} veže launch za pregledač koji je započeo prijavu (zaštita od podmetnutog launch-a).
     * Proba se svaka vrednost (podmetnut istoimeni kolačić za {@code .trif.rs} ne smeta pravom).
     */
    private static void proveriKolacic(HttpServletRequest req, LtiStanje s) {
        List<String> vrednosti = Kolacici.procitajSve(req, Kolacici.LTI);
        if (vrednosti.isEmpty()) {
            throw new LtiOdbijeno(LtiGreska.BEZ_KOLACICA, Kolacici.LTI);
        }
        byte[] ocekivano = s.getKolacicHes().getBytes(StandardCharsets.UTF_8);
        boolean odgovara = vrednosti.stream()
                .anyMatch(v -> MessageDigest.isEqual(TokenHash.od(v).getBytes(StandardCharsets.UTF_8), ocekivano));
        if (!odgovara) {
            throw new LtiOdbijeno(LtiGreska.ISTEKLA_SESIJA, Kolacici.LTI + "-drugi");
        }
    }

    /** Vrednost {@code __Host-gfs_lti} koju pregledač već ima (ispravnog oblika), da je druga kartica ne pregazi. */
    private static Optional<String> postojeciKolacic(HttpServletRequest req) {
        return Kolacici.procitajSve(req, Kolacici.LTI).stream()
                .filter(v -> TokenGenerator.FORMAT.matcher(v).matches())
                .findFirst();
    }

    static List<String> uloge(JWTClaimsSet c) {
        Object u = c.getClaim(ULOGE);
        if (!(u instanceof List<?> lista)) {
            return List.of();
        }
        return lista.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    /** Instructor (i podvrste, npr. asistent) ili Administrator, pun URI ili kratko ime. */
    static boolean jeNastavnik(String uloga) {
        return uloga.equals("Instructor") || uloga.equals("Administrator")
                || uloga.endsWith("#Instructor") || uloga.endsWith("#Administrator")
                || uloga.contains("/membership/Instructor#");
    }

    /** Learner (i podvrste), pun URI ili kratko ime. */
    static boolean jeStudent(String uloga) {
        return uloga.equals("Learner") || uloga.endsWith("#Learner") || uloga.contains("/membership/Learner#");
    }

    /**
     * Ime za ekran povezivanja: {@code name}, ili {@code given_name} + {@code family_name}; bez kontrolnih i
     * nevidljivih znakova (i smera pisanja), niz razmaka postaje jedan razmak, obrezano, najviše 100 znakova;
     * {@code null} kad Moodle ime ne deli.
     */
    static String ime(JWTClaimsSet c) {
        String ime = cisto(tekst(c, "name"));
        if (ime == null) {
            String delovi = java.util.stream.Stream.of(cisto(tekst(c, "given_name")), cisto(tekst(c, "family_name")))
                    .filter(x -> x != null).collect(Collectors.joining(" "));
            ime = delovi.isEmpty() ? null : delovi;
        }
        if (ime == null) {
            return null;
        }
        return ime.codePoints().limit(NAJDUZE_IME)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().strip();
    }

    private static String tekst(JWTClaimsSet c, String claim) {
        Object v = c.getClaim(claim);
        return v instanceof String s ? s : null;
    }

    private static String cisto(String s) {
        if (s == null) return null;
        String b = s.codePoints().filter(cp -> !NEVIDLJIVO.contains(Character.getType(cp)))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString()
                .replaceAll("[\\p{Z}\\s]+", " ").strip();
        return b.isEmpty() ? null : b;
    }

    private static void parametar(StringBuilder u, String ime, String vrednost) {
        u.append(ime).append('=').append(enc(vrednost)).append('&');
    }

    /** Form-enkodovanje ({@code +} postaje {@code %2B}, razmak {@code +}): PHP ga dekodira tačno. */
    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String obrezi(String s) {
        return s == null ? null : s.strip();
    }

    private static boolean prazno(String s) {
        return s == null || s.isBlank();
    }

    private static String bezZavrsneKose(String s) {
        if (s == null) return null;
        String u = s;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
