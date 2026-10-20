package tri.novica.gfssystem.service.lti;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.entity.lti.LtiKljuc;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.lti.LtiKljucRepository;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ključ alata za LTI 1.3 (spec 8.2): RSA 2048 se pravi pri prvom zahtevu i čuva u {@code lti_kljucevi} (privatni kao
 * PKCS#8 base64, javni kao JWK JSON, {@code kid} od 16 znakova {@link TokenGenerator}-a). Ostaje isti posle restarta,
 * jer se ništa ne drži samo u memoriji. Privatni deo se nikad ne loguje ni ne izlazi iz {@link #aktivni()}.
 * <p>
 * Launch ključ ne koristi (potpis platforme proverava njen JWKS); Moodle traži javni keyset u formi alata, a trebaće
 * za ocene i deep linking. Rotacija: nov red, stari ostaje u JWKS-u dok je {@code aktivan}.
 */
@Service
@Slf4j
public class LtiKljucServis {

    private static final int DUZINA_KID = 16;

    private final LtiKljucRepository repo;
    private final TokenGenerator tokeni;
    private final Clock clock;
    private final TransactionTemplate transakcija;
    /** Pravljenje prvog ključa je jedno po procesu (jedan backend); drugi zahtev čeka pa nađe gotov red. */
    private final ReentrantLock pravljenje = new ReentrantLock();

    public LtiKljucServis(LtiKljucRepository repo, TokenGenerator tokeni, Clock clock,
                          PlatformTransactionManager tm) {
        this.repo = repo;
        this.tokeni = tokeni;
        this.clock = clock;
        this.transakcija = new TransactionTemplate(tm);
        // nova transakcija: commit je pre otpuštanja brave i kad pozivalac već ima spoljnu transakciju (drugi tok ne pravi drugi ključ)
        this.transakcija.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Najnoviji aktivni ključ sa privatnim delom; pravi ga ako ga nema. */
    public RSAKey aktivni() {
        List<LtiKljuc> aktivni = repo.findAllByAktivanTrueOrderByKreiranoDescIdDesc();
        if (aktivni.isEmpty()) {
            aktivni = List.of(napraviAkoNema());
        }
        return uJwk(aktivni.get(0));
    }

    /** Javni JWKS (svi aktivni ključevi, samo javni delovi); pravi prvi ključ ako ga nema. */
    public JWKSet javni() {
        List<LtiKljuc> aktivni = repo.findAllByAktivanTrueOrderByKreiranoDescIdDesc();
        if (aktivni.isEmpty()) {
            aktivni = List.of(napraviAkoNema());
        }
        return new JWKSet(aktivni.stream().map(this::uJavni).toList());
    }

    private LtiKljuc napraviAkoNema() {
        pravljenje.lock();
        try {
            return transakcija.execute(status -> {
                List<LtiKljuc> postoji = repo.findAllByAktivanTrueOrderByKreiranoDescIdDesc();
                if (!postoji.isEmpty()) return postoji.get(0);
                LtiKljuc novi = napravi();
                log.info("LTI: napravljen ključ alata, kid={}", novi.getKid());
                return repo.save(novi);
            });
        } finally {
            pravljenje.unlock();
        }
    }

    private LtiKljuc napravi() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair par = g.generateKeyPair();
            String kid = tokeni.novi().substring(0, DUZINA_KID);
            RSAKey jwk = new RSAKey.Builder((RSAPublicKey) par.getPublic())
                    .keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
            LtiKljuc k = new LtiKljuc();
            k.setKid(kid);
            k.setPrivatni(Base64.getEncoder().encodeToString(par.getPrivate().getEncoded()));
            k.setJavni(jwk.toJSONString());
            k.setKreirano(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS));
            k.setAktivan(true);
            return k;
        } catch (GeneralSecurityException e) {
            throw new SystemException("Ključ alata nije moguće napraviti.", e, 500);
        }
    }

    /** Samo javni JWK iz baze; privatni ključ se ne dira (anonimni JWKS zahtev). */
    private JWK uJavni(LtiKljuc k) {
        try {
            return RSAKey.parse(k.getJavni()).toPublicJWK();
        } catch (ParseException e) {
            throw new SystemException("Ključ alata je oštećen (kid=" + k.getKid() + ").", 500);
        }
    }

    private RSAKey uJwk(LtiKljuc k) {
        try {
            RSAKey javni = RSAKey.parse(k.getJavni());
            RSAPrivateKey privatni = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(k.getPrivatni())));
            return new RSAKey.Builder(javni).privateKey(privatni).build();
        } catch (ParseException | GeneralSecurityException | IllegalArgumentException e) {
            // bez poruke izuzetka: ne sme da nosi ni delić ključa
            throw new SystemException("Ključ alata je oštećen (kid=" + k.getKid() + ").", 500);
        }
    }
}
