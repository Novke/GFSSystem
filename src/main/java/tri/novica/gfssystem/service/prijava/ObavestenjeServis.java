package tri.novica.gfssystem.service.prijava;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.config.NastavnikSvojstva;
import tri.novica.gfssystem.dto.prijava.BrojObavestenjaInfo;
import tri.novica.gfssystem.dto.prijava.ObavestenjeInfo;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.Obavestenje;
import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.prijava.ObavestenjeRepository;
import tri.novica.gfssystem.repository.prijava.StudentskiUredjajRepository;
import tri.novica.gfssystem.service.mejl.MejlPoruke;
import tri.novica.gfssystem.service.mejl.MejlServis;
import tri.novica.gfssystem.service.raspored.TerminOpis;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.IndeksUtil;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Obaveštenja za nastavnika (spec 6): {@code NE_STIZE_KOD} ("Ne stiže mi kod", javni zahtev studenta) i
 * {@code PREVISE_KODOVA} (student je dostigao limit od 3 koda na sat), plus lista, brojač i čitanje/rešavanje.
 * <p>
 * <b>"Ne stiže mi kod" ne sme da odaje da li nalog postoji</b> (isto kao {@link KodServis#posalji}): {@link #neStizeKod}
 * nikad ne javlja razliku između postojećeg studenta, studenta bez mejla i nepostojećeg indeksa. Limit 1/h po indeksu je
 * tih (nema greške ni drugačijeg odgovora), samo limit po IP-u (10/h) daje 429, i to pre donje granice trajanja (ne zavisi od
 * indeksa). Indeks se normalizuje i proverava belom listom istim kodom kao slanje koda ({@link KodServis#indeks},
 * {@link KodServis#nadjiStudenta}), a trajanje drži ista donja granica ({@link KodServis#sacekajDoGranice}), pa
 * razlika od nekoliko upisa ne izlazi u vreme odgovora. Svaki korak u bazi je kratka sopstvena transakcija; između
 * transakcija i dok se čeka donja granica nit ne drži vezu iz pula (open-in-view je isključen za {@code /public/nalog/**}).
 * <p>
 * Istovremeni zahtevi za isti indeks idu redom kroz MySQL bravu {@code gfs-pomoc:<hmac>} (kao {@code KodServis}), pa dva
 * paralelna zahteva ne prave dva obaveštenja ni dva mejla. Mejl nastavniku ide posle commit-a, asinhrono
 * ({@code MejlPosleCommita}), uz limite {@code MEJL_NASTAVNIKU} (ključ {@code vrsta:HMAC indeksa}, 1/h) i
 * {@code MEJL_NASTAVNIKU_UKUPNO} (20/h); bez {@code gfs.nastavnik.mejl} ili bez SMTP-a nema mejla ni brojača.
 * <p>
 * Log: IP i id-jevi, indeks samo kao prvih 8 hex SHA-256; nikad ime, mejl ni token.
 */
@Service
@Slf4j
public class ObavestenjeServis {

    static final String PREVISE = "Previše pokušaja. Pokušaj ponovo kasnije.";
    static final String NASLOV_POMOC = "GFS: studentu ne stiže kod";
    static final String NASLOV_PREVISE = "GFS: previše zahteva za kod";
    static final String NASLOV_MNOGO_NAJAVA = "GFS: mnogo najava odsustva";
    static final Duration SAT = Duration.ofHours(1);
    static final Duration DAN = Duration.ofDays(1);
    static final int LIMIT_POMOC_IP = 10;
    static final int LIMIT_POMOC_INDEKS = 1;
    static final int LIMIT_MEJL_INDEKS = 1;
    static final int LIMIT_MEJL_UKUPNO = 20;
    /** Ključ ukupnog brojača mejlova nastavniku. */
    static final String UKUPNO = "ukupno";
    /** MySQL brava za proveru i upis ukupnog brojača mejlova nastavniku. */
    static final String BRAVA_MEJL = "gfs-mejl-nastavniku";
    /** Podrazumevano čekanje brave ukupnog brojača (s) za "Ne stiže mi kod"; menja se samo u testovima. */
    static final int CEKANJE_BRAVE_MEJL_S = 5;
    /** Čekanje te brave (s) iz {@link #previseKodova}, koja drži red studenta ({@code FOR UPDATE}): kratko, najviše ovoliko. */
    static final int CEKANJE_BRAVE_MEJL_PREVISE_S = 1;
    /** Telo mejla kad nema studenta: stalna rečenica, bez ičega što je ukucano u javnu formu. */
    static final String NEPOZNAT_INDEKS_TELO = "Neko je tražio pomoć za indeks koji ne postoji u evidenciji. "
            + "Detalji su u aplikaciji (Obaveštenja).\n";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ObavestenjeRepository obavestenja;
    private final StudentRepository studenti;
    private final StudentskiUredjajRepository uredjaji;
    private final PokusajiServis pokusaji;
    private final KodServis kodovi;
    private final MejlServis mejl;
    private final MejlPoruke poruke;
    private final NastavnikSvojstva nastavnik;
    private final ApplicationEventPublisher dogadjaji;
    private final Clock clock;
    private final TransactionTemplate zakljucano;
    /** Koliko se najduže čeka brava ukupnog brojača mejlova (s); u testovima spušteno ili podignuto. */
    private volatile int cekanjeBraveMejlS = CEKANJE_BRAVE_MEJL_S;

    public ObavestenjeServis(ObavestenjeRepository obavestenja, StudentRepository studenti,
                             StudentskiUredjajRepository uredjaji, PokusajiServis pokusaji, KodServis kodovi,
                             MejlServis mejl, MejlPoruke poruke, NastavnikSvojstva nastavnik, ApplicationEventPublisher dogadjaji,
                             PlatformTransactionManager transakcije, Clock clock) {
        this.obavestenja = obavestenja;
        this.studenti = studenti;
        this.uredjaji = uredjaji;
        this.pokusaji = pokusaji;
        this.kodovi = kodovi;
        this.mejl = mejl;
        this.poruke = poruke;
        this.nastavnik = nastavnik;
        this.dogadjaji = dogadjaji;
        this.clock = clock;
        // READ COMMITTED: posle čekanja na bravu/red čita se ono što je prethodni držalac upisao (kao u KodServis)
        this.zakljucano = new TransactionTemplate(transakcije);
        this.zakljucano.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    // ------------------------------------------------------------------ "Ne stiže mi kod"

    /** Isto kao {@link #neStizeKod(String, Integer, String, List)}, stari zahtev bez godine, najviše jedan token uređaja. */
    public void neStizeKod(String indeks, String ip, Optional<String> studentskiToken) {
        neStizeKod(indeks, null, ip, studentskiToken.map(List::of).orElse(List.of()));
    }

    /** Stari zahtev bez godine (PRIVREMENO, vidi {@link KodServis}). */
    public void neStizeKod(String indeks, String ip, List<String> studentskiTokeni) {
        neStizeKod(indeks, null, ip, studentskiTokeni);
    }

    /**
     * Student javlja da mu kod ne stiže. Uvek se vraća bez greške (poruka "Javili smo nastavniku." je ista za postojeći
     * nalog, nalog bez mejla i nepostojeći indeks), osim 429 preko 10/h po IP-u. Najviše jedno obaveštenje na sat po
     * indeksu; ostali zahtevi se ćutke preskaču. {@code studentskiTokeni} su sve vrednosti kolačića {@code gfs_student}
     * zahteva (više njih kad sestrinski poddomen podmetne svoj): {@code uredjaj} je {@code ZAPAMCEN} kad bar jedan
     * pripada uređaju tog studenta (i opozvanom), inače {@code NEPOZNAT}.
     */
    public void neStizeKod(String indeks, Integer godina, String ip, List<String> studentskiTokeni) {
        long pocetak = System.nanoTime();
        // 429 po IP-u ne zavisi od indeksa, pa ide bez donje granice (kao u KodServis.posalji)
        if (pokusaji.dostignut(VrstaPokusaja.POMOC_IP, ip, LIMIT_POMOC_IP, SAT)) {
            log.info("Pomoć za kod odbijena (limit po IP-u), ip={}", ip);
            throw new SystemException(PREVISE, 429);
        }
        try {
            pokusaji.zabelezi(VrstaPokusaja.POMOC_IP, ip);
            // HMAC ključ se pravi pri prvom pozivu, van zaključane transakcije
            KodServis.Indeks i = kodovi.indeks(indeks, godina);
            zakljucano.executeWithoutResult(tx -> pomocZakljucano(i, ip, studentskiTokeni));
        } finally {
            kodovi.sacekajDoGranice(pocetak);
        }
    }

    private void pomocZakljucano(KodServis.Indeks i, String ip, List<String> tokeni) {
        if (!kodovi.uzmiBravu("gfs-pomoc:", i)) {
            // drugi zahtev za isti indeks drži bravu duže od 10 s: njegovo obaveštenje pokriva i ovaj zahtev
            log.info("Pomoć za kod preskočena (brava indeksa zauzeta), indeks={}, ip={}", i.trag(), ip);
            return;
        }
        String kljuc = KodServis.kljucIndeksaIzHmac(i.hmac());
        if (pokusaji.dostignut(VrstaPokusaja.POMOC_INDEKS, kljuc, LIMIT_POMOC_INDEKS, SAT)) {
            log.info("Pomoć za kod preskočena (limit po indeksu), indeks={}, ip={}", i.trag(), ip);
            return;
        }
        pokusaji.zabeleziUTransakciji(VrstaPokusaja.POMOC_INDEKS, kljuc);
        Optional<Student> student = kodovi.nadjiStudenta(i);
        boolean zapamcen = student.isPresent() && pripadaUredjaju(student.get().getId(), tokeni);

        Map<String, Object> podaci = new LinkedHashMap<>();
        podaci.put("indeks", i.n());
        podaci.put("godina", i.godina());
        podaci.put("imaMejl", student.map(s -> s.getEmail() != null && !s.getEmail().isBlank()).orElse(false));
        podaci.put("ip", ip);
        podaci.put("uredjaj", zapamcen ? "ZAPAMCEN" : "NEPOZNAT");
        Obavestenje o = snimi(VrstaObavestenja.NE_STIZE_KOD, student.orElse(null), podaci);
        log.info("Obaveštenje 'ne stiže kod' napravljeno, obavestenje={}, student={}, indeks={}, ip={}", o.getId(),
                student.map(Student::getId).orElse(null), i.trag(), ip);
        mejlNastavniku(VrstaObavestenja.NE_STIZE_KOD, kljuc, i.trag(), NASLOV_POMOC,
                telo(VrstaObavestenja.NE_STIZE_KOD, student.map(ObavestenjeServis::indeksSaGodinom).orElse(null),
                        student.map(Student::getId).orElse(null)), cekanjeBraveMejlS);
    }

    /** Da li bar jedan token pripada uređaju studenta (opozvan i istekao se računaju: pamti se da je uređaj bio njegov). */
    private boolean pripadaUredjaju(Long studentId, List<String> tokeni) {
        for (String token : tokeni) {
            if (token != null && TokenGenerator.FORMAT.matcher(token).matches()
                    && uredjaji.findByTokenHes(TokenHash.od(token))
                    .filter(u -> u.getStudent().getId().equals(studentId)).isPresent()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ previše kodova

    /**
     * Student je dostigao limit od 3 koda na sat ({@link #naDostignutLimitKodova}, u pozadini): obaveštenje {@code PREVISE_KODOVA},
     * najviše jedno u 24 sata po studentu (rešeno ili ne). Red studenta je zaključan u toj transakciji, pa istovremeni
     * pozivi ne prave dva. Ne baca izuzetak zbog nepostojećeg studenta.
     */
    public void previseKodova(Long studentId, String ip) {
        zakljucano.executeWithoutResult(tx -> {
            Optional<Student> student = studenti.findByIdForUpdate(studentId);
            if (student.isEmpty()) {
                return;
            }
            LocalDateTime sada = LocalDateTime.now(clock);
            if (obavestenja.existsByVrstaAndStudentIdAndKreiranoAfter(VrstaObavestenja.PREVISE_KODOVA, studentId,
                    sada.minus(DAN))) {
                return;
            }
            Student st = student.get();
            String n = IndeksUtil.kanonski(st.getIndeks(), st.getGodina());
            Map<String, Object> podaci = new LinkedHashMap<>();
            podaci.put("indeks", n == null ? "" : n);
            podaci.put("godina", st.getGodina());
            podaci.put("ip", ip);
            Obavestenje o = snimi(VrstaObavestenja.PREVISE_KODOVA, student.get(), podaci);
            log.info("Obaveštenje 'previše kodova' napravljeno, obavestenje={}, student={}, ip={}", o.getId(), studentId, ip);
            String identitet = KodServis.Indeks.identitet(n == null ? "" : n, st.getGodina());
            // red studenta je zaključan: brava mejla se čeka kratko (najviše 1 s), da zastoj na njoj ne drži taj red
            mejlNastavniku(VrstaObavestenja.PREVISE_KODOVA, kodovi.kljucIndeksa(identitet),
                    KodServis.trag(identitet), NASLOV_PREVISE,
                    telo(VrstaObavestenja.PREVISE_KODOVA, indeksSaGodinom(st), st.getId()),
                    Math.min(CEKANJE_BRAVE_MEJL_PREVISE_S, cekanjeBraveMejlS));
        });
    }

    /**
     * Obrada {@link DostignutLimitKodova} u pozadini ({@code obavestenjaIzvrsilac}), posle odgovora 429: zahtev ne radi
     * ništa više u bazi za postojećeg studenta nego za nepostojeći indeks. Neuspeh se samo loguje (klasa izuzetka i id
     * studenta): obaveštenje nikad ne sme da utiče na pozivaoca.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("obavestenjaIzvrsilac")
    public void naDostignutLimitKodova(DostignutLimitKodova d) {
        try {
            previseKodova(d.studentId(), d.ip());
        } catch (RuntimeException e) {
            // samo klasa izuzetka: poruka može da nosi podatke iz upita
            log.error("Obaveštenje o previše kodova nije upisano, student={}: {}", d.studentId(), e.getClass().getSimpleName());
        }
    }

    /** Menja čekanje brave ukupnog brojača mejlova (s, testovi); {@link #CEKANJE_BRAVE_MEJL_S} je podrazumevano. */
    void cekanjeBraveMejla(int sekundi) {
        cekanjeBraveMejlS = sekundi;
    }

    private Obavestenje snimi(VrstaObavestenja vrsta, Student student, Map<String, Object> podaci) {
        Obavestenje o = new Obavestenje();
        o.setVrsta(vrsta);
        o.setStudent(student);
        o.setPodaci(JSON.writeValueAsString(podaci));
        o.setKreirano(LocalDateTime.now(clock));
        o = obavestenja.save(o);
        dogadjaji.publishEvent(new NovoObavestenje(vrsta, null, 0, 0));
        return o;
    }

    // ------------------------------------------------------------------ mejl nastavniku

    /**
     * Mejl nastavniku uz obaveštenje, u tekućoj transakciji (šalje se posle commit-a). Bez adrese ({@code
     * gfs.nastavnik.mejl}) ili bez SMTP-a: ništa, ni brojač. Naslov i telo sastavlja pozivalac samo iz podataka iz baze:
     * naslov nema ime studenta; telo za poznatog studenta ima njegov <b>upisan</b> indeks i relativni link na karticu
     * ({@code studenti/<id>}), bez studenta samo stalnu rečenicu, a za termin ({@code MNOGO_NAJAVA}) broj, predmet, grupu i
     * vreme. Ono što je student ili napadač ukucao (indeks u javnoj formi, razlog najave) nikad ne ide u mejl nastavniku
     * (vidi se u aplikaciji, gde ga Angular escape-uje). Najviše 1 mejl po (vrsta, ključ) na sat i 20 na sat ukupno;
     * provera i upis oba brojača idu pod MySQL bravom {@value #BRAVA_MEJL}, inače bi istovremeni zahtevi za različite
     * ključeve prešli ukupni limit. Redosled brava: brava pomoći, red studenta ili red stavke rasporeda, pa ova (poslednja;
     * pozivaoci koji drže red čekaju je najviše 1 s). Ne dobije li se brava, mejl se preskače (obaveštenje u bazi postoji).
     *
     * @param kljucBrojaca ključ brojača po (vrsta, ključ): HMAC indeksa ({@link KodServis#kljucIndeksa}) ili termin
     * @param trag         samo za log (prvih 8 hex indeksa, ili id termina)
     * @param cekanjeS     koliko (s) se najduže čeka brava ukupnog brojača
     */
    private void mejlNastavniku(VrstaObavestenja vrsta, String kljucBrojaca, String trag, String naslov, String telo,
                                int cekanjeS) {
        String adresa = nastavnik.mejl();
        if (adresa.isEmpty() || !mejl.dostupan()) {
            return;
        }
        if (!kodovi.uzmiBravu(BRAVA_MEJL, cekanjeS, "mejl-nastavniku")) {
            log.warn("Mejl nastavniku preskočen (brava zauzeta), vrsta={}, kljuc={}", vrsta, trag);
            return;
        }
        String kljuc = vrsta.name() + ":" + kljucBrojaca;
        if (pokusaji.dostignut(VrstaPokusaja.MEJL_NASTAVNIKU, kljuc, LIMIT_MEJL_INDEKS, SAT)
                || pokusaji.dostignut(VrstaPokusaja.MEJL_NASTAVNIKU_UKUPNO, UKUPNO, LIMIT_MEJL_UKUPNO, SAT)) {
            log.info("Mejl nastavniku preskočen (limit), vrsta={}, kljuc={}", vrsta, trag);
            return;
        }
        pokusaji.zabeleziUTransakciji(VrstaPokusaja.MEJL_NASTAVNIKU, kljuc);
        pokusaji.zabeleziUTransakciji(VrstaPokusaja.MEJL_NASTAVNIKU_UKUPNO, UKUPNO);
        dogadjaji.publishEvent(poruke.nastavniku(adresa, naslov, telo));
    }

    // ------------------------------------------------------------------ mnogo najava odsustva

    /**
     * Za termin {@code t} odsustvo je najavilo {@code broj} studenata, više od praga perioda (spec najave odsustva, odeljak
     * 5): pravi <b>jedno</b> obaveštenje {@code MNOGO_NAJAVA} po terminu (ključ {@link TerminOpis#kljucObavestenja()}); ako
     * ono već postoji (otvoreno ili rešeno), ne radi ništa, pa ni pad pa rast broja ne pravi drugo. Mora se zvati u
     * transakciji koja drži red stavke ({@code RasporedStavkaRepository.findByIdForUpdate}): sve najave stavke idu redom,
     * pa je provera tačna (jedinstven ključ u bazi je druga linija). Mejl nastavniku kroz iste limite, sa brojem, predmetom,
     * grupom i vremenom; nikad imena ni razlozi studenata.
     *
     * @return da li je obaveštenje napravljeno
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean mnogoNajava(TerminOpis t, long broj, int prag) {
        String kljuc = t.kljucObavestenja();
        if (obavestenja.existsByKljuc(kljuc)) {
            return false;
        }
        Map<String, Object> podaci = new LinkedHashMap<>();
        podaci.put("stavkaId", t.stavkaId());
        podaci.put("datum", t.datum().toString());
        podaci.put("pocetak", TerminOpis.hhmm(t.pocetak()));
        podaci.put("kraj", TerminOpis.hhmm(t.kraj()));
        podaci.put("predmet", t.predmet());
        podaci.put("grupa", t.grupa());
        podaci.put("broj", broj);
        podaci.put("prag", prag);
        Obavestenje o = new Obavestenje();
        o.setVrsta(VrstaObavestenja.MNOGO_NAJAVA);
        o.setKljuc(kljuc);
        o.setPodaci(JSON.writeValueAsString(podaci));
        o.setKreirano(LocalDateTime.now(clock));
        o = obavestenja.save(o);
        dogadjaji.publishEvent(new NovoObavestenje(VrstaObavestenja.MNOGO_NAJAVA, t, broj, prag));
        log.info("Obaveštenje 'mnogo najava' napravljeno, obavestenje={}, stavka={}, datum={}, broj={}", o.getId(),
                t.stavkaId(), t.datum(), broj);
        mejlNastavniku(VrstaObavestenja.MNOGO_NAJAVA, t.stavkaId() + ":" + t.datum(), "stavka " + t.stavkaId(),
                NASLOV_MNOGO_NAJAVA, teloMnogoNajava(t, broj, prag),
                Math.min(CEKANJE_BRAVE_MEJL_PREVISE_S, cekanjeBraveMejlS));
        return true;
    }

    /** Rešava obaveštenje {@code MNOGO_NAJAVA} termina (otkazan termin); vraća koliko ih je rešeno (0 ili 1). */
    @Transactional
    public int resiZaTermin(TerminOpis t) {
        return obavestenja.resiPoKljucu(t.kljucObavestenja(), LocalDateTime.now(clock));
    }

    /** Telo mejla za {@code MNOGO_NAJAVA}: samo podaci iz baze (predmet, grupa, vreme) i brojevi. */
    static String teloMnogoNajava(TerminOpis t, long broj, int prag) {
        return """
                Za čas iz predmeta %s (grupa %s) %s %s od %s odsustvo je najavilo %s (prag je %d).

                Termini: raspored/termini?od=%s

                Obaveštenja su u aplikaciji, u listi iz gornje trake.
                """.formatted(t.predmet(), t.grupa(), t.uDan(), t.datumPun(), t.blok().replace("–", " do "),
                TerminOpis.studenata(broj), prag, t.datum());
    }

    /** Upisan indeks studenta sa godinom upisa, za mejl ({@code GD5/2026}). */
    static String indeksSaGodinom(Student s) {
        String n = IndeksUtil.kanonski(s.getIndeks(), s.getGodina());
        return n == null ? "" : n + "/" + s.getGodina();
    }

    /** {@code indeks} i {@code studentId} su iz baze, ili oba {@code null} kad nema studenta (tada ništa ukucano). */
    static String telo(VrstaObavestenja vrsta, String indeks, Long studentId) {
        if (studentId == null) {
            return NEPOZNAT_INDEKS_TELO;
        }
        String opis = vrsta == VrstaObavestenja.NE_STIZE_KOD
                ? "Student javlja da mu ne stiže kod za prijavu."
                : "Studentu je u poslednjih sat vremena poslato 3 koda za prijavu, pa je dostignut limit.";
        return """
                %s

                Indeks: %s
                Kartica studenta: studenti/%d

                Obaveštenja su u aplikaciji, u listi iz gornje trake.
                """.formatted(opis, indeks == null ? "" : indeks, studentId);
    }

    // ------------------------------------------------------------------ lista i čitanje (nastavnik)

    /** Obaveštenja, najnovija prva; {@code samoOtvorena} izostavlja rešena. Bez sorta u zahtevu: {@code kreirano} opadajuće. */
    @Transactional(readOnly = true)
    public Page<ObavestenjeInfo> lista(boolean samoOtvorena, Pageable pageable) {
        Pageable p = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("kreirano"), Sort.Order.desc("id")));
        Page<Obavestenje> strana = samoOtvorena ? obavestenja.findAllByResenoIsNull(p) : obavestenja.findAll(p);
        return strana.map(ObavestenjeServis::info);
    }

    @Transactional(readOnly = true)
    public BrojObavestenjaInfo broj() {
        return new BrojObavestenjaInfo(obavestenja.countByProcitanoIsNull(), obavestenja.countByResenoIsNull());
    }

    /** Označava pročitanim; ponovljeno ne menja vreme. 404 kad ne postoji. */
    @Transactional
    public void procitano(Long id) {
        Obavestenje o = nadji(id);
        if (o.getProcitano() == null) {
            o.setProcitano(LocalDateTime.now(clock));
        }
    }

    /** Označava rešenim (i pročitanim); ponovljeno ne menja vreme. 404 kad ne postoji. */
    @Transactional
    public void reseno(Long id) {
        Obavestenje o = nadji(id);
        LocalDateTime sada = LocalDateTime.now(clock);
        if (o.getReseno() == null) {
            o.setReseno(sada);
        }
        if (o.getProcitano() == null) {
            o.setProcitano(sada);
        }
    }

    /** Označava sva nepročitana pročitanim; vraća koliko ih je bilo. */
    @Transactional
    public int procitajSve() {
        return obavestenja.procitajSve(LocalDateTime.now(clock));
    }

    /** Rešava otvorena obaveštenja studenta (slanje koda sa kartice, spec 6); vraća koliko ih je bilo. */
    @Transactional
    public int resiZaStudenta(Long studentId) {
        return obavestenja.resiZaStudenta(studentId, LocalDateTime.now(clock));
    }

    private Obavestenje nadji(Long id) {
        return obavestenja.findById(id).orElseThrow(() -> new SystemException("Ne postoji.", 404));
    }

    @SuppressWarnings("unchecked")
    private static ObavestenjeInfo info(Obavestenje o) {
        Map<String, Object> podaci;
        try {
            podaci = JSON.readValue(o.getPodaci(), Map.class);
        } catch (RuntimeException e) {
            podaci = Map.of();
        }
        Student s = o.getStudent();
        String indeks = s != null ? s.getIndeks()
                : podaci.get("indeks") instanceof String str ? str : null;
        String ime = s == null ? null : ((s.getIme() == null ? "" : s.getIme()) + " "
                + (s.getPrezime() == null ? "" : s.getPrezime())).trim();
        return new ObavestenjeInfo(o.getId(), o.getVrsta(), s == null ? null : s.getId(), ime, indeks, podaci,
                o.getKreirano(), o.getProcitano(), o.getReseno(),
                s != null ? Integer.valueOf(s.getGodina()) : podaci.get("godina") instanceof Number g ? g.intValue() : null);
    }
}
