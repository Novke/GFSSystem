package tri.novica.gfssystem.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.push.PushPretplataCmd;
import tri.novica.gfssystem.dto.push.PushStanjeInfo;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.entity.push.PushPretplata;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.push.PushPretplataRepository;
import tri.novica.gfssystem.service.prijava.PokusajiServis;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Push pretplate (spec aplikacije 5.2 i 5.4): pretplata pripada <b>uređaju iz sesije</b> (studentski uređaj ili sesija
 * nastavnika, {@link Vlasnik}), nikad id-ju iz zahteva. Jedan uređaj ima najviše jednu pretplatu (nova zamenjuje staru), a
 * isti endpoint na drugom uređaju se premešta na novi (zajednički telefon posle odjave jednog i prijave drugog studenta ne
 * dobija tuđa obaveštenja). Upis do {@value #LIMIT_UPISA}/h i proba do {@value #LIMIT_PROBE}/h po uređaju
 * ({@code pokusaji}); svaki pokušaj se broji, i neispravan.
 */
@Service
@Slf4j
public class PushPretplate {

    public static final String NIJE_PODRZAN = "Ovaj browser nije podržan za obaveštenja.";
    public static final String NEISPRAVNA = "Neispravna pretplata.";
    public static final String NEMA_PRETPLATE = "Ovaj uređaj nema uključena obaveštenja.";
    public static final String PREVISE = "Previše pokušaja. Pokušaj ponovo kasnije.";
    static final int LIMIT_UPISA = 20;
    static final int LIMIT_PROBE = 5;
    static final Duration SAT = Duration.ofHours(1);
    static final Duration TTL_PROBE = Duration.ofHours(1);
    /** Najviše znakova ključa pre dekodiranja (base64url od 65 bajtova je 87 znakova, od 16 bajtova 22). */
    private static final int MAX_KLJUC = 200;

    /** Vlasnik pretplate iz sesije: tačno jedno od dva. */
    public record Vlasnik(Long uredjajId, Long sesijaId) {

        public static Vlasnik uredjaj(Long id) {
            return new Vlasnik(id, null);
        }

        public static Vlasnik sesija(Long id) {
            return new Vlasnik(null, id);
        }

        public Vlasnik {
            if ((uredjajId == null) == (sesijaId == null)) {
                throw new IllegalArgumentException("Vlasnik pretplate je tačno jedan uređaj ili jedna sesija");
            }
        }

        boolean student() {
            return uredjajId != null;
        }

        /** Ključ brojača u {@code pokusaji}. */
        String kljuc() {
            return student() ? "u:" + uredjajId : "s:" + sesijaId;
        }

        boolean poseduje(PushPretplata p) {
            return student() ? uredjajId.equals(p.getStudentskiUredjajId()) : sesijaId.equals(p.getNastavnickaSesijaId());
        }
    }

    private final PushPretplataRepository repository;
    private final PushKljucevi kljucevi;
    private final PushPosiljalac posiljalac;
    private final PokusajiServis pokusaji;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transakcija;
    private final Clock clock;

    public PushPretplate(PushPretplataRepository repository, PushKljucevi kljucevi, PushPosiljalac posiljalac,
                         PokusajiServis pokusaji, JdbcTemplate jdbc, PlatformTransactionManager transakcije, Clock clock) {
        this.repository = repository;
        this.kljucevi = kljucevi;
        this.posiljalac = posiljalac;
        this.pokusaji = pokusaji;
        this.jdbc = jdbc;
        this.transakcija = new TransactionTemplate(transakcije);
        this.transakcija.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clock = clock;
    }

    /** Javni VAPID ključ i da li ovaj uređaj (sesija) ima pretplatu. */
    public PushStanjeInfo stanje(Vlasnik v) {
        String javni = kljucevi.javniKljuc();
        Boolean ima = transakcija.execute(s -> v.student() ? repository.existsByStudentskiUredjajId(v.uredjajId())
                : repository.existsByNastavnickaSesijaId(v.sesijaId()));
        return new PushStanjeInfo(javni, Boolean.TRUE.equals(ima));
    }

    /**
     * Upisuje ili zamenjuje pretplatu ovog uređaja. 429 preko {@value #LIMIT_UPISA}/h; 400 {@value #NIJE_PODRZAN} za
     * endpoint van liste push servisa, 400 {@value #NEISPRAVNA} za neispravan endpoint ili ključeve.
     */
    public void pretplati(Vlasnik v, PushPretplataCmd cmd) {
        if (pokusaji.dostignut(VrstaPokusaja.PUSH_PRETPLATA, v.kljuc(), LIMIT_UPISA, SAT)) {
            log.info("Upis push pretplate odbijen (limit), {}", v.kljuc());
            throw new SystemException(PREVISE, HttpStatus.TOO_MANY_REQUESTS);
        }
        pokusaji.zabelezi(VrstaPokusaja.PUSH_PRETPLATA, v.kljuc());
        Ispravna p = proveri(cmd);
        transakcija.executeWithoutResult(s -> upisi(v, p));
    }

    /** Briše pretplatu ovog uređaja (i kad je nema). */
    public void odjavi(Vlasnik v) {
        Integer n = transakcija.execute(s -> v.student() ? repository.obrisiZaUredjaj(v.uredjajId())
                : repository.obrisiZaSesiju(v.sesijaId()));
        if (n != null && n > 0) {
            log.info("Push pretplata obrisana na zahtev, {}", v.kljuc());
        }
    }

    /** Probno obaveštenje na ovaj uređaj; 409 bez pretplate, 429 preko {@value #LIMIT_PROBE}/h. */
    public void proba(Vlasnik v) {
        if (pokusaji.dostignut(VrstaPokusaja.PUSH_PROBA, v.kljuc(), LIMIT_PROBE, SAT)) {
            log.info("Probno push obaveštenje odbijeno (limit), {}", v.kljuc());
            throw new SystemException(PREVISE, HttpStatus.TOO_MANY_REQUESTS);
        }
        pokusaji.zabelezi(VrstaPokusaja.PUSH_PROBA, v.kljuc());
        Optional<PushPretplata> p = transakcija.execute(s -> v.student()
                ? repository.findByStudentskiUredjajId(v.uredjajId()) : repository.findByNastavnickaSesijaId(v.sesijaId()));
        if (p == null || p.isEmpty()) {
            throw new SystemException(NEMA_PRETPLATE, HttpStatus.CONFLICT);
        }
        posiljalac.pretplati(p.get(), PushTekstovi.proba(v.student()), TTL_PROBE, false);
    }

    /** Proverena pretplata, ključevi u kanonskom obliku (base64url bez dopune). */
    record Ispravna(String endpoint, String hes, String p256dh, String auth) {
    }

    static Ispravna proveri(PushPretplataCmd cmd) {
        if (cmd == null) {
            throw new SystemException(NEISPRAVNA, HttpStatus.BAD_REQUEST);
        }
        switch (PushEndpoint.proveri(cmd.endpoint())) {
            case NEISPRAVAN -> throw new SystemException(NEISPRAVNA, HttpStatus.BAD_REQUEST);
            case NEPODRZAN -> throw new SystemException(NIJE_PODRZAN, HttpStatus.BAD_REQUEST);
            case DOZVOLJEN -> { }
        }
        byte[] p256dh = kljuc(cmd.p256dh());
        byte[] auth = kljuc(cmd.auth());
        if (p256dh == null || auth == null || auth.length != 16 || !P256.ispravnaTacka(p256dh)) {
            throw new SystemException(NEISPRAVNA, HttpStatus.BAD_REQUEST);
        }
        return new Ispravna(cmd.endpoint(), PushEndpoint.hes(cmd.endpoint()), Base64Url.kodiraj(p256dh),
                Base64Url.kodiraj(auth));
    }

    private static byte[] kljuc(String s) {
        return s == null || s.length() > MAX_KLJUC ? null : Base64Url.dekodiraj(s);
    }

    /**
     * Pod zaključanim redom vlasnika (dva upisa istog uređaja idu redom): isti endpoint kod drugog vlasnika se briše, pa
     * se pretplata ovog vlasnika menja na mestu ili pravi. Nestao uređaj ili sesija (odjava u međuvremenu): 401.
     */
    private void upisi(Vlasnik v, Ispravna p) {
        List<Long> red = v.student()
                ? jdbc.queryForList("SELECT id FROM studentski_uredjaji WHERE id = ? FOR UPDATE", Long.class, v.uredjajId())
                : jdbc.queryForList("SELECT id FROM nastavnicke_sesije WHERE id = ? FOR UPDATE", Long.class, v.sesijaId());
        if (red.isEmpty()) {
            throw new SystemException("Potrebna je prijava.", HttpStatus.UNAUTHORIZED);
        }
        LocalDateTime sada = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        Optional<PushPretplata> isti = repository.findByEndpointHes(p.hes());
        if (isti.isPresent() && !v.poseduje(isti.get())) {
            repository.delete(isti.get());
            repository.flush();
            log.info("Push endpoint premešten na drugi uređaj, {}, trag={}", v.kljuc(), PushEndpoint.trag(p.endpoint()));
        }
        PushPretplata x = (v.student() ? repository.findByStudentskiUredjajId(v.uredjajId())
                : repository.findByNastavnickaSesijaId(v.sesijaId())).orElseGet(PushPretplata::new);
        boolean nova = x.getId() == null;
        x.setEndpoint(p.endpoint());
        x.setEndpointHes(p.hes());
        x.setP256dh(p.p256dh());
        x.setAuth(p.auth());
        x.setStudentskiUredjajId(v.uredjajId());
        x.setNastavnickaSesijaId(v.sesijaId());
        x.setUzastopneGreske(0);
        if (nova) {
            x.setKreirano(sada);
        }
        repository.saveAndFlush(x);
        log.info("Push pretplata {}, {}, trag={}", nova ? "upisana" : "zamenjena", v.kljuc(), PushEndpoint.trag(p.endpoint()));
    }
}
