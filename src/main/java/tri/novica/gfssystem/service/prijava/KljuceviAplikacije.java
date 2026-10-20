package tri.novica.gfssystem.service.prijava;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.entity.prijava.KljucAplikacije;
import tri.novica.gfssystem.repository.prijava.KljucAplikacijeRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tajni ključevi aplikacije u bazi ({@code kljucevi_aplikacije}, spec 9): 32 nasumična bajta (base64), red se pravi pri prvoj
 * upotrebi i posle se samo čita (i pamti u memoriji). Kad dve instance aplikacije (ili dve niti pre keša) istovremeno prave
 * isti ključ, {@code UNIQUE(naziv)} pusti samo jedan red; drugi dobije {@link DataIntegrityViolationException} i pročita
 * pobednikov, pa svi koriste isti ključ. Vrednost se nikad ne loguje.
 * <p>
 * Pravljenje i čitanje idu u sopstvenoj transakciji ({@code REQUIRES_NEW}): neuspeo {@code INSERT} ne kvari pozivaočevu
 * transakciju. Zato se prvi poziv ne radi dok pozivalac drži zaključan red (to bi tražilo drugu vezu iz pula).
 */
@Service
@Slf4j
public class KljuceviAplikacije {

    /** Ključ za HMAC lažne maske mejla ({@link MaskaMejla#lazna}). */
    public static final String MASKA_HMAC = "MASKA_HMAC";
    static final int DUZINA = 32;

    private final KljucAplikacijeRepository repository;
    private final TransactionTemplate novaTransakcija;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, byte[]> kes = new ConcurrentHashMap<>();

    public KljuceviAplikacije(KljucAplikacijeRepository repository, PlatformTransactionManager transakcije, Clock clock) {
        this.repository = repository;
        this.novaTransakcija = new TransactionTemplate(transakcije);
        this.novaTransakcija.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Ključ {@code naziv} (32 bajta); pravi ga kad još ne postoji. Vraća kopiju. */
    public byte[] kljuc(String naziv) {
        return kes.computeIfAbsent(naziv, this::procitajIliNapravi).clone();
    }

    private byte[] procitajIliNapravi(String naziv) {
        Optional<byte[]> postojeci = procitaj(naziv);
        if (postojeci.isPresent()) {
            return postojeci.get();
        }
        byte[] nov = new byte[DUZINA];
        random.nextBytes(nov);
        try {
            novaTransakcija.executeWithoutResult(s -> {
                KljucAplikacije k = new KljucAplikacije();
                k.setNaziv(naziv);
                k.setVrednost(Base64.getEncoder().encodeToString(nov));
                k.setKreirano(LocalDateTime.now(clock));
                repository.saveAndFlush(k);
            });
            log.info("Napravljen ključ aplikacije {}", naziv);
            return nov;
        } catch (DataIntegrityViolationException e) {
            // neko drugi ga je upravo napravio: važi njegov
            log.info("Ključ aplikacije {} je napravljen istovremeno na drugom mestu, čita se postojeći", naziv);
            return procitaj(naziv).orElseThrow(() -> new IllegalStateException("Ključ aplikacije nije upisan: " + naziv));
        }
    }

    private Optional<byte[]> procitaj(String naziv) {
        return novaTransakcija.execute(s -> repository.findByNaziv(naziv).map(k -> dekodiraj(naziv, k.getVrednost())));
    }

    private static byte[] dekodiraj(String naziv, String vrednost) {
        byte[] b = Base64.getDecoder().decode(vrednost);
        if (b.length != DUZINA) {
            throw new IllegalStateException("Ključ aplikacije " + naziv + " nema " + DUZINA + " bajta");
        }
        return b;
    }
}
