package tri.novica.gfssystem.service.prijava;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.config.NastavnikSvojstva;
import tri.novica.gfssystem.dto.prijava.SesijaInfo;
import tri.novica.gfssystem.entity.prijava.NastavnickaSesija;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.prijava.NastavnickaSesijaRepository;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Prijava nastavnika i njegove sesije po uređaju (spec 2.2, 4): korisnik i bcrypt heš lozinke su iz okruženja
 * ({@link NastavnikSvojstva}), sesija je red u {@code nastavnicke_sesije} sa SHA-256 tokena iz kolačića, 90 dana od
 * poslednjeg korišćenja. Neuspele prijave se broje po IP-u ({@link PokusajiServis}): 10 u 15 minuta zaključava IP.
 * Log ima samo IP i id sesije, nikad korisničko ime, lozinku ni token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NastavnikSesijaServis {

    /** Trajanje sesije od poslednjeg korišćenja; isto je Max-Age kolačića {@code gfs_nastavnik}. */
    public static final Duration TRAJANJE = Duration.ofDays(90);
    static final Duration PRODUZENJE_NAJCESCE = Duration.ofMinutes(10);
    static final int LIMIT_GRESAKA = 10;
    static final Duration PROZOR_GRESAKA = Duration.ofMinutes(15);
    /** bcrypt gleda samo prvih 72 bajta; duže lozinke su pogrešne (i ne smeju da ruše poređenje). */
    private static final int MAX_BAJTOVA_LOZINKE = 72;

    private final NastavnikSvojstva svojstva;
    private final NastavnickaSesijaRepository repository;
    private final PokusajiServis pokusaji;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /** Vraća sirov token za kolačić {@code gfs_nastavnik}. Namerno bez transakcije: bcrypt ne drži vezu sa bazom. */
    public String prijavi(String korisnik, String lozinka, String ip, String userAgent) {
        if (!svojstva.podeseno()) {
            log.warn("Prijava nastavnika nije podešena (gfs.nastavnik.*), ip={}", ip);
            throw new SystemException("Prijava nastavnika nije podešena.", 503);
        }
        if (pokusaji.dostignut(VrstaPokusaja.NASTAVNIK_GRESKA_IP, ip, LIMIT_GRESAKA, PROZOR_GRESAKA)) {
            log.warn("Prijava nastavnika blokirana zbog previše pokušaja, ip={}", ip);
            throw new SystemException("Previše pokušaja. Pokušaj ponovo za 15 minuta.", 429);
        }

        boolean imeOk = MessageDigest.isEqual(bajtovi(korisnik), bajtovi(svojstva.korisnik()));
        boolean lozinkaOk = lozinkaOdgovara(lozinka);
        if (!(imeOk & lozinkaOk)) {
            pokusaji.zabelezi(VrstaPokusaja.NASTAVNIK_GRESKA_IP, ip);
            log.info("Neuspela prijava nastavnika, ip={}", ip);
            throw new SystemException("Pogrešno korisničko ime ili lozinka.", 401);
        }

        String token = tokenGenerator.novi();
        LocalDateTime sada = LocalDateTime.now(clock);
        NastavnickaSesija s = new NastavnickaSesija();
        s.setTokenHes(TokenHash.od(token));
        s.setOpis(OpisUredjaja.iz(userAgent));
        s.setIp(ip);
        s.setKreirano(sada);
        s.setPoslednjiUlaz(sada);
        s.setIstice(sada.plus(TRAJANJE));
        repository.save(s);
        log.info("Prijava nastavnika, sesija={}, ip={}", s.getId(), ip);
        return token;
    }

    /**
     * Važeća sesija za token iz kolačića (nije opozvana, {@code istice} je u budućnosti). Korišćenje pomera
     * {@code poslednjiUlaz} i {@code istice} najviše jednom na 10 minuta (ne piše se u bazu pri svakom zahtevu).
     */
    @Transactional
    public Optional<NastavnickaSesija> potvrdi(String token) {
        if (token == null || !TokenGenerator.FORMAT.matcher(token).matches()) {
            return Optional.empty();
        }
        LocalDateTime sada = LocalDateTime.now(clock);
        Optional<NastavnickaSesija> nadjena = repository.findByTokenHes(TokenHash.od(token))
                .filter(s -> s.getOpozvana() == null && s.getIstice().isAfter(sada));
        nadjena.ifPresent(s -> {
            if (!s.getPoslednjiUlaz().plus(PRODUZENJE_NAJCESCE).isAfter(sada)) {
                s.setPoslednjiUlaz(sada);
                s.setIstice(sada.plus(TRAJANJE));
                repository.save(s);
            }
        });
        return nadjena;
    }

    /** Odjava ovog uređaja; nepoznat token se ćutke preskače. */
    @Transactional
    public void odjavi(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        repository.findByTokenHes(TokenHash.od(token)).ifPresent(this::opozoviSesiju);
    }

    @Transactional(readOnly = true)
    public List<SesijaInfo> sesije(Long trenutnaId) {
        return repository.findAllByOpozvanaIsNullAndIsticeAfterOrderByPoslednjiUlazDesc(LocalDateTime.now(clock)).stream()
                .map(s -> new SesijaInfo(s.getId(), s.getOpis(), s.getKreirano(), s.getPoslednjiUlaz(),
                        s.getId().equals(trenutnaId)))
                .toList();
    }

    @Transactional
    public void opozovi(Long id) {
        NastavnickaSesija s = repository.findById(id)
                .orElseThrow(() -> new SystemException("Sesija ne postoji.", 404));
        opozoviSesiju(s);
    }

    /** Opoziva sve važeće sesije osim trenutne; vraća broj opozvanih. */
    @Transactional
    public int odjaviSveOsim(Long trenutnaId) {
        List<NastavnickaSesija> ostale = repository
                .findAllByOpozvanaIsNullAndIsticeAfterOrderByPoslednjiUlazDesc(LocalDateTime.now(clock)).stream()
                .filter(s -> !s.getId().equals(trenutnaId))
                .toList();
        ostale.forEach(this::opozoviSesiju);
        return ostale.size();
    }

    private void opozoviSesiju(NastavnickaSesija s) {
        if (s.getOpozvana() == null) {
            s.setOpozvana(LocalDateTime.now(clock));
            repository.save(s);
            log.info("Nastavnička sesija opozvana, sesija={}", s.getId());
        }
    }

    /** bcrypt se računa uvek (i kad je ime pogrešno), da trajanje ne odaje koji je deo pogrešan. */
    private boolean lozinkaOdgovara(String lozinka) {
        String l = lozinka == null ? "" : lozinka;
        boolean predugacka = bajtovi(l).length > MAX_BAJTOVA_LOZINKE;
        boolean poklapa = bcrypt.matches(predugacka ? "" : l, svojstva.lozinkaBcrypt());
        return poklapa && !predugacka;
    }

    private static byte[] bajtovi(String s) {
        return (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
    }
}
