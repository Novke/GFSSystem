package tri.novica.gfssystem.service.fajlovi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.fajlovi.FajlCasa;
import tri.novica.gfssystem.dto.fajlovi.FajloviCasa;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.entity.fajlovi.KodFajlova;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.fajlovi.KodFajlovaRepository;
import tri.novica.gfssystem.service.prijava.PokusajiServis;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Fajlovi na času po kratkom kodu, bez prijave (spec fajlova, odeljak 5): spisak {@code GET /public/fajlovi/{kod}} i
 * preuzimanje. Pogrešan ili nevažeći kod (i onaj koji nije četiri cifre) je 404 {@value #KOD_NE_VAZI} i pokušaj u
 * {@code pokusaji} ({@link VrstaPokusaja#KOD_FAJLOVA}, ključ = puna adresa klijenta kao u {@code KodServis}); posle
 * {@value #LIMIT} pogrešnih u {@code 15} minuta po IP-u je 429 za svaki kod, i ispravan, dok prozor ne prođe. Ispravan
 * kod ništa ne troši, pa razred iza jednog NAT-a preuzima koliko hoće. Pravilo "šta je na spisku" je u
 * {@link PristupFajlovima}, važenje koda u {@link KodoviFajlova}; ovde samo broj pokušaja i gradnja odgovora. DTO se gradi u
 * kratkoj readOnly transakciji ({@code /public/fajlovi/**} je van open-in-view).
 * <p>
 * <b>Bez transakcije na klasi:</b> {@code PokusajiServis.zabelezi} je {@code REQUIRES_NEW}; pozvan iz otvorene transakcije
 * držao bi dve konekcije po zahtevu, pa bi ~10 istovremenih pogrešnih kodova iscrpelo pool i zaustavilo ceo backend. Pokušaj
 * se zato upisuje tek posle zatvorene transakcije čitanja. Broj koji je skoro izdat ({@value #NEDAVNO_SATI} h) pa istekao
 * (završeno predavanje, stari tab koji se sam osvežava) je 404 bez pokušaja: nije pogađanje, a inače bi učionica sa
 * otvorenim starim linkovima potrošila limit celog NAT-a.
 */
@Service
@Slf4j
public class FajloviCasaServis {

    public static final String KOD_NE_VAZI = "Kod ne važi. Proveri broj sa table.";
    public static final String PREVISE_POGRESNIH = "Previše pogrešnih kodova. Pokušaj ponovo za 15 minuta.";
    public static final int LIMIT = 40;
    public static final Duration PROZOR = Duration.ofMinutes(15);
    /** Koliko dugo posle isteka se broj još prepoznaje kao izdat (gornja granica važenja koda izvođenja). */
    public static final int NEDAVNO_SATI = 12;

    private final KodoviFajlova kodovi;
    private final PristupFajlovima pristup;
    private final PokusajiServis pokusaji;
    private final KodFajlovaRepository kodRepository;
    private final Clock clock;
    private final TransactionTemplate citanje;

    public FajloviCasaServis(KodoviFajlova kodovi, PristupFajlovima pristup, PokusajiServis pokusaji,
                             KodFajlovaRepository kodRepository, Clock clock, PlatformTransactionManager tm) {
        this.kodovi = kodovi;
        this.pristup = pristup;
        this.pokusaji = pokusaji;
        this.kodRepository = kodRepository;
        this.clock = clock;
        this.citanje = new TransactionTemplate(tm);
        this.citanje.setReadOnly(true);
    }

    /** Fajlovi važećeg koda redom iz spec-a; predmet i tema iz predavanja (ili prezentacije kad nema predavanja). */
    public FajloviCasa spisak(String kod, String ip) {
        proveriLimit(ip);
        Optional<FajloviCasa> rez = citanje.execute(s -> kodovi.vazeci(kod).map(this::gradi));
        return rez.orElseThrow(() -> pogresan(kod, ip));
    }

    /** Fajl sa spiska koda; fajl van spiska je isto što i pogrešan kod (404 {@value #KOD_NE_VAZI}), ali ne troši pokušaj. */
    public Preuzet preuzmi(String kod, Long fajlId, String ip) {
        proveriLimit(ip);
        Optional<Optional<Preuzet>> rez = citanje.execute(s -> kodovi.vazeci(kod).map(k -> pristup.fajloviKoda(k).stream()
                .filter(x -> x.getId().equals(fajlId)).findFirst()
                .map(f -> new Preuzet(f.getNaziv(), f.getMedij().getId()))));
        if (rez.isEmpty()) throw pogresan(kod, ip);
        return rez.get().orElseThrow(() -> new SystemException(KOD_NE_VAZI, HttpStatus.NOT_FOUND));
    }

    private FajloviCasa gradi(KodFajlova k) {
        List<FajlCasa> fajlovi = pristup.fajloviKoda(k).stream().map(FajlMapper::fajlCasa).toList();
        if (k.getPredavanje() != null) {
            var p = k.getPredavanje();
            return new FajloviCasa(k.getAktivanKod(), p.getPredmet().getNaziv(), p.getTema(), p.getDatum(), fajlovi);
        }
        Izvodjenje iz = k.getIzvodjenje();
        return new FajloviCasa(k.getAktivanKod(), iz.getPrezentacija().getPredmet().getNaziv(), iz.getPrezentacija().getNaziv(),
                iz.getPocetak().toLocalDate(), fajlovi);
    }

    /** Šta se preuzima: ime i id medija; bajtove učitava pozivalac ({@link MedijService#ucitaj}), van transakcije. */
    public record Preuzet(String naziv, String medijId) {
    }

    private void proveriLimit(String ip) {
        if (pokusaji.dostignut(VrstaPokusaja.KOD_FAJLOVA, ip, LIMIT, PROZOR)) {
            log.info("Kod fajlova odbijen (limit po IP-u), ip={}", ip);
            throw new SystemException(PREVISE_POGRESNIH, HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /** Pogrešan kod: pokušaj se upisuje van transakcije, osim za broj koji je skoro izdat pa istekao. */
    private SystemException pogresan(String kod, String ip) {
        boolean nedavnoIzdat = kod != null && kod.length() == 4
                && kodRepository.existsByKodAndVaziDoAfter(kod, LocalDateTime.now(clock).minusHours(NEDAVNO_SATI));
        if (!nedavnoIzdat) {
            pokusaji.zabelezi(VrstaPokusaja.KOD_FAJLOVA, ip);
        }
        return new SystemException(KOD_NE_VAZI, HttpStatus.NOT_FOUND);
    }
}
