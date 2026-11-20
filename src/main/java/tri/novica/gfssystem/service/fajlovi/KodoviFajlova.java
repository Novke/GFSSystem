package tri.novica.gfssystem.service.fajlovi;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.fajlovi.KodFajlova;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.repository.fajlovi.KodFajlovaRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Kratki kodovi za fajlove na času (spec fajlova, odeljak 4): {@code /f/<kod>}, četiri cifre 1000-9999, bez prijave.
 * <ul>
 * <li>{@link #obezbedi}: poziva se pri pokretanju izvođenja, u istoj transakciji. Izvođenje sa predavanjem dobija kod
 * predavanja (jedan red po predavanju; važi {@link #KOD_PREDAVANJA} = 5 h kao čekiranje; postojeći važeći kod se deli sa
 * svim izvođenjima tog predavanja, istekao dobija novi broj u istom redu). Izvođenje bez predavanja dobija svoj red koji
 * važi dok je AKTIVNO, najduže {@link #KOD_IZVODJENJA}. Pre izbora broja se oslobađaju brojevi isteklih kodova.</li>
 * <li>{@link #vazeci}: kod važi samo kad red postoji sa {@code aktivan_kod}, vreme nije isteklo i predavanje nije završeno
 * (odnosno izvođenje je AKTIVNO). Kod se ne briše pri "Završi": prestaje da važi proverom pri čitanju.</li>
 * </ul>
 * Broj se bira {@link #POKUSAJA} puta {@code SecureRandom}-om protiv {@code UNIQUE(aktivan_kod)}; upis je native
 * ({@code INSERT IGNORE} / {@code UPDATE IGNORE}) i proverava broj redova, pa duplikat ne baca izuzetak i ne kvari sesiju.
 * Posle {@link #POKUSAJA} neuspeha je log greške i izvođenje kreće bez koda ({@code Optional.empty()}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KodoviFajlova {

    /** Kod predavanja važi koliko je student "na času" posle čekiranja. */
    public static final Duration KOD_PREDAVANJA = Duration.ofHours(5);
    /** Gornja granica koda izvođenja bez predavanja (izvođenje se samo završi posle 12 h). */
    public static final Duration KOD_IZVODJENJA = Duration.ofHours(12);
    public static final int POKUSAJA = 30;
    private static final Pattern FORMAT = Pattern.compile("^[0-9]{4}$");

    private final KodFajlovaRepository kodovi;
    private final EntityManager em;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Kod za izvođenje koje se upravo pokreće (u transakciji pozivaoca), ili prazno kad ga nema: predavanje je već
     * završeno ili 30 pokušaja nije našlo slobodan broj.
     */
    public Optional<KodFajlova> obezbedi(Izvodjenje iz) {
        LocalDateTime sada = LocalDateTime.now(clock);
        Predavanje predavanje = iz.getPredavanje();
        if (predavanje != null && Boolean.TRUE.equals(predavanje.getZavrseno())) {
            return Optional.empty();
        }
        kodovi.oslobodiIstekle(sada);
        Duration trajanje = predavanje != null ? KOD_PREDAVANJA : KOD_IZVODJENJA;
        for (int pokusaj = 0; pokusaj < POKUSAJA; pokusaj++) {
            // red se čita iznova: drugi zahtev ga je mogao upisati, a native upis ne osvežava već učitane entitete
            Optional<KodFajlova> red = predavanje != null ? kodovi.findByPredavanjeId(predavanje.getId())
                    : kodovi.findByIzvodjenjeId(iz.getId());
            if (red.isPresent()) {
                em.refresh(red.get());
                if (red.get().getAktivanKod() != null && sada.isBefore(red.get().getVaziDo())) {
                    return red;
                }
            }
            String broj = String.valueOf(1000 + random.nextInt(9000));
            LocalDateTime vaziDo = sada.plus(trajanje);
            int upisano;
            if (red.isPresent()) {
                upisano = kodovi.obnovi(red.get().getId(), broj, sada, vaziDo);
            } else if (predavanje != null) {
                upisano = kodovi.upisiZaPredavanje(broj, predavanje.getId(), sada, vaziDo);
            } else {
                upisano = kodovi.upisiZaIzvodjenje(broj, iz.getId(), sada, vaziDo);
            }
            if (upisano == 1) {
                KodFajlova k = kodovi.findByAktivanKod(broj).orElseThrow();
                em.refresh(k);
                return Optional.of(k);
            }
        }
        log.error("Kod fajlova nije izdat: {} pokušaja bez slobodnog broja (izvođenje {})", POKUSAJA, iz.getId());
        return Optional.empty();
    }

    /** Važeći kod ili prazno (loš format, nepoznat, istekao, završeno predavanje, izvođenje koje nije AKTIVNO). */
    @Transactional(readOnly = true)
    public Optional<KodFajlova> vazeci(String kod) {
        if (kod == null || !FORMAT.matcher(kod).matches()) {
            return Optional.empty();
        }
        Optional<KodFajlova> red = kodovi.findByAktivanKod(kod);
        if (red.isEmpty()) {
            return red;
        }
        KodFajlova k = red.get();
        em.refresh(k);   // sveže stanje: kod je mogao biti oslobođen ili obnovljen posle učitavanja
        if (k.getAktivanKod() == null || !LocalDateTime.now(clock).isBefore(k.getVaziDo())) {
            return Optional.empty();
        }
        if (k.getPredavanje() != null) {
            return Boolean.TRUE.equals(k.getPredavanje().getZavrseno()) ? Optional.empty() : red;
        }
        if (k.getIzvodjenje() != null) {
            return k.getIzvodjenje().getStatus() == StatusIzvodjenja.AKTIVNO ? red : Optional.empty();
        }
        return Optional.empty();
    }
}
