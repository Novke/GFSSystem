package tri.novica.gfssystem.service.test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.test.online.*;
import tri.novica.gfssystem.entity.Polaganje;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.Test;
import tri.novica.gfssystem.entity.TipTesta;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.entity.test.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.KoeficijentiOcenjivanjaRepository;
import tri.novica.gfssystem.repository.PolaganjeRepository;
import tri.novica.gfssystem.repository.TestRepository;
import tri.novica.gfssystem.repository.prijava.StudentskiUredjajRepository;
import tri.novica.gfssystem.repository.test.TestDogadjajRepository;
import tri.novica.gfssystem.repository.test.TestPrimerakRepository;
import tri.novica.gfssystem.repository.test.TestPrimerakZadatakRepository;
import tri.novica.gfssystem.repository.test.TestSmenaRepository;
import tri.novica.gfssystem.service.portal.KontekstStudenta;
import tri.novica.gfssystem.service.prijava.PokusajiServis;
import tri.novica.gfssystem.utility.Prolaz;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Polaganje online testa za studenta (spec 12 i 17, plan 5). Pravila za svaki poziv:
 * <ul>
 *   <li>student je uvek iz sesije; tuđ ili nepostojeći test (i test u pripremi) je 404 {@value KontekstStudenta#NEMA};</li>
 *   <li>zadaci tek posle „Pokreni“ i ulaska; nikad ključ, rešenje ni udeo pre objave rezultata (sa tačnim odgovorima
 *       samo uz „Pokaži tačne odgovore“);</li>
 *   <li>svaka izmena prvo zaključa red primerka; test je otvoren na jednom uređaju (drugi traži „Nastavi ovde“);</li>
 *   <li>izmena stigla do 10 s posle roka se prima; posle toga 409 i primerak se zaključava sa sačuvanim.</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PolaganjeOnlineTesta {

    public static final String DRUGI_UREDJAJ = "Test je otvoren na drugom uređaju.";
    public static final String POGRESAN_KOD = "Pogrešan ulazni kod.";
    public static final String ZASTARELA = "Zastarela izmena.";
    public static final String ISTEKLO = "Vreme je isteklo.";
    public static final String PREDAT = "Test je predat.";
    public static final String NIJE_U_TOKU = "Test nije u toku.";
    /** Najviše znakova u jednom tekstualnom ili brojčanom polju. */
    public static final int MAX_TEKST = 200;
    private static final int MAX_POGRESNIH_KODOVA = 10;
    private static final int MAX_DOGADJAJA = 500;

    private final TestRepository testRepository;
    private final TestSmenaRepository smenaRepository;
    private final TestPrimerakRepository primerakRepository;
    private final TestPrimerakZadatakRepository zadatakRepository;
    private final TestDogadjajRepository dogadjajRepository;
    private final PolaganjeRepository polaganjeRepository;
    private final KoeficijentiOcenjivanjaRepository koeficijentiRepository;
    private final StudentskiUredjajRepository uredjajRepository;
    private final OcenjivanjeOnlineTesta ocenjivanje;
    private final OnlineTestService online;
    private final KontekstStudenta kontekst;
    private final PokusajiServis pokusaji;
    private final Clock clock;

    // ---------------------------------------------------------------- spisak i stanje

    /** Testovi otvoreni za ulaz ili započeti, čija smena nije zatvorena. */
    @Transactional(readOnly = true)
    public List<TestNaNalogu> spisak(long studentId) {
        List<TestNaNalogu> rezultat = new ArrayList<>();
        for (TestPrimerak p : primerakRepository.zaStudenta(studentId)) {
            Test t = testRepository.findById(p.getTestId()).orElse(null);
            if (t == null || !t.isOnline() || p.getStanje() == StanjePrimerka.PONISTEN) continue;
            TestSmena s = smenaRepository.findById(p.getSmenaId()).orElse(null);
            if (s == null || s.getUlazOtvoren() == null || s.getZatvorena() != null) continue;
            rezultat.add(new TestNaNalogu(t.getId(), t.getTipTesta().getNaziv(), t.getPredmet().getNaziv(), t.getDatum(),
                    t.getTrajanjeMin() == null ? 0 : t.getTrajanjeMin(), StanjeTesta.faza(s, p),
                    s.getUlazniKod() != null && p.getStanje() == StanjePrimerka.DODELJEN));
        }
        rezultat.sort(Comparator.comparing(TestNaNalogu::datum).thenComparing(TestNaNalogu::testId));
        return rezultat;
    }

    public PolaganjeStudenta ulaz(long studentId, Long uredjajId, Long testId, UlazCmd cmd, String ip) {
        Kontekst k = zakljucaj(studentId, testId);
        TestPrimerak p = k.primerak();
        LocalDateTime sada = LocalDateTime.now(clock);
        istek(k, sada);
        if (k.smena().getZatvorena() != null || !StanjeTesta.otvoren(p)) {
            return stanje(k, uredjajId);
        }
        boolean preuzmi = cmd != null && Boolean.TRUE.equals(cmd.preuzmi());
        if (p.getStanje() == StanjePrimerka.DODELJEN) {
            String kod = k.smena().getUlazniKod();
            if (kod != null) {
                String kljuc = "primerak:" + p.getId();
                if (pokusaji.dostignut(VrstaPokusaja.TEST_ULAZNI_KOD, kljuc, MAX_POGRESNIH_KODOVA, Duration.ofMinutes(15))) {
                    throw new SystemException("Previše pokušaja. Pokušaj za nekoliko minuta.", HttpStatus.TOO_MANY_REQUESTS);
                }
                String dato = cmd == null || cmd.kod() == null ? "" : cmd.kod().strip();
                if (!kod.equals(dato)) {
                    pokusaji.zabelezi(VrstaPokusaja.TEST_ULAZNI_KOD, kljuc);
                    throw new SystemException(POGRESAN_KOD, HttpStatus.FORBIDDEN);
                }
            }
            p.setStanje(StanjePrimerka.U_TOKU);
            p.setUsao(sada);
            p.setAktivniUredjajId(uredjajId);
            p.setIp(ip);
            online.dogadjaj(p, "USAO", null, sada);
        } else if (!Objects.equals(p.getAktivniUredjajId(), uredjajId)) {
            if (!preuzmi) throw new SystemException(DRUGI_UREDJAJ, HttpStatus.CONFLICT);
            p.setAktivniUredjajId(uredjajId);
            p.setIp(ip);
            online.dogadjaj(p, "PREUZEO", null, sada);
        }
        primerakRepository.save(p);
        return stanje(k, uredjajId);
    }

    public PolaganjeStudenta stanje(long studentId, Long uredjajId, Long testId) {
        Kontekst k = zakljucaj(studentId, testId);
        istek(k, LocalDateTime.now(clock));
        TestPrimerak p = k.primerak();
        if (p.getStanje() == StanjePrimerka.U_TOKU && k.smena().getZatvorena() == null
                && !Objects.equals(p.getAktivniUredjajId(), uredjajId)) {
            throw new SystemException(DRUGI_UREDJAJ, HttpStatus.CONFLICT);
        }
        return stanje(k, uredjajId);
    }

    // ---------------------------------------------------------------- čuvanje i predaja

    public Sacuvano sacuvaj(long studentId, Long uredjajId, Long testId, Long zadatakId, CuvanjeCmd cmd) {
        Kontekst k = zakljucaj(studentId, testId);
        TestPrimerak p = k.primerak();
        List<TestPrimerakZadatak> zadaci = zadatakRepository.zaPrimerakForUpdate(p.getId());
        TestPrimerakZadatak z = zadaci.stream().filter(x -> x.getId().equals(zadatakId)).findFirst()
                .orElseThrow(KontekstStudenta::nema);
        proveriIzmenu(k, uredjajId);
        if (cmd == null || cmd.rev() == null) throw OnlineTestService.bad("Redni broj izmene je obavezan.");
        if (cmd.rev() != z.getRev()) throw new SystemException(ZASTARELA, HttpStatus.CONFLICT);
        SnimakZadatkaTesta sn = TestJson.snimak(z.getSnimak());
        Map<String, Object> uneto = proveriUnos(sn.poljaZaUnos(), cmd.uneto());
        z.setOdgovori(uneto.isEmpty() ? null : TestJson.uJson(uneto));
        z.setRev(z.getRev() + 1);
        z.setSacuvano(LocalDateTime.now(clock));
        zadatakRepository.save(z);
        return new Sacuvano(z.getRev(), z.getSacuvano());
    }

    public PolaganjeStudenta predaj(long studentId, Long uredjajId, Long testId) {
        Kontekst k = zakljucaj(studentId, testId);
        TestPrimerak p = k.primerak();
        LocalDateTime sada = LocalDateTime.now(clock);
        if (p.getStanje() == StanjePrimerka.PREDAT || p.getStanje() == StanjePrimerka.PONISTEN) {
            throw new SystemException(PREDAT, HttpStatus.CONFLICT);
        }
        if (istek(k, sada)) {
            return stanje(k, uredjajId);
        }
        if (p.getStanje() != StanjePrimerka.U_TOKU || k.smena().getPokrenuta() == null || k.smena().getZatvorena() != null) {
            throw new SystemException(NIJE_U_TOKU, HttpStatus.CONFLICT);
        }
        if (!Objects.equals(p.getAktivniUredjajId(), uredjajId)) throw new SystemException(DRUGI_UREDJAJ, HttpStatus.CONFLICT);
        online.predaj(k.test(), p, NacinPredaje.STUDENT, sada, ocenjivanje.ispravke(k.test().getId()));
        log.info("Online test predat: test={}, primerak={}", k.test().getId(), p.getId());
        return stanje(k, uredjajId);
    }

    /** Meki znak sa ekrana (napustio prozor, vratio se); ništa ne blokira, posle predaje se ne beleži. */
    public void dogadjaj(long studentId, Long uredjajId, Long testId, DogadjajCmd cmd) {
        if (cmd == null || !("NAPUSTIO".equals(cmd.vrsta()) || "VRATIO".equals(cmd.vrsta()))) {
            throw OnlineTestService.bad("Nepoznata vrsta događaja.");
        }
        if (cmd.trajanjeMs() != null && (cmd.trajanjeMs() < 0 || cmd.trajanjeMs() > 86_400_000L)) {
            throw OnlineTestService.bad("Neispravno trajanje.");
        }
        Kontekst k = zakljucaj(studentId, testId);
        TestPrimerak p = k.primerak();
        if (p.getStanje() != StanjePrimerka.U_TOKU || k.smena().getZatvorena() != null) return;
        if (!Objects.equals(p.getAktivniUredjajId(), uredjajId)) return;
        if (dogadjajRepository.findByPrimerakIdOrderByVremeAscIdAsc(p.getId()).size() >= MAX_DOGADJAJA) return;
        online.dogadjaj(p, cmd.vrsta(), "VRATIO".equals(cmd.vrsta()) && cmd.trajanjeMs() != null
                ? String.valueOf(cmd.trajanjeMs()) : null, LocalDateTime.now(clock));
    }

    // ---------------------------------------------------------------- rezultati

    /** Objavljeni rezultati studenta na predmetu (i testovi na papiru), sa oznakom koje se polaganje računa. */
    @Transactional(readOnly = true)
    public List<RezultatTestaKratko> rezultatiPredmeta(long studentId, Long predmetId) {
        List<Polaganje> objavljena = polaganjeRepository.objavljenaZaStudentaIPredmet(studentId, predmetId);
        // predmet koji student sluša (predavanje grupe) ili na kome ima objavljen test; inače 404 kao tuđ predmet
        if (objavljena.isEmpty()) kontekst.zaPredmet(studentId, predmetId);
        boolean max = koeficijentiRepository.findByPredmetId(predmetId)
                .map(k -> Boolean.TRUE.equals(k.getKoristiMaxRezultat())).orElse(false);
        List<Polaganje> sva = polaganjeRepository.findAllByStudentAndPredmet(studentId, predmetId).stream()
                .filter(p -> !p.getTest().isOnline() || Boolean.TRUE.equals(p.getTest().getPregledan())).toList();
        Map<Long, Long> racunaSe = new HashMap<>();   // tip testa -> id polaganja koje se računa
        Map<Long, List<Polaganje>> poTipu = new HashMap<>();
        sva.forEach(p -> poTipu.computeIfAbsent(p.getTest().getTipTesta().getId(), x -> new ArrayList<>()).add(p));
        poTipu.forEach((tip, l) -> {
            Polaganje izabrano = null;
            for (Polaganje p : l) {
                if (p.getOstvareniPoeni() == null) continue;
                if (izabrano == null) {
                    izabrano = p;
                } else if (max ? p.getOstvareniPoeni() > izabrano.getOstvareniPoeni()
                        : poslednje(p, izabrano)) {
                    izabrano = p;
                }
            }
            if (izabrano != null) racunaSe.put(tip, izabrano.getId());
        });
        List<RezultatTestaKratko> rezultat = new ArrayList<>();
        for (Polaganje p : objavljena) {
            Test t = p.getTest();
            TipTesta tip = t.getTipTesta();
            rezultat.add(new RezultatTestaKratko(t.getId(), t.getDatum(), tip.getNaziv(), p.getOstvareniPoeni(),
                    t.getMaxPoena() == null ? 0 : t.getMaxPoena(),
                    Prolaz.polozenoIliNull(p.getOstvareniPoeni(), t.getPragProlaza(), p.getPrepisivao()),
                    p.getId().equals(racunaSe.get(tip.getId())), t.isOnline()));
        }
        return rezultat;
    }

    private static boolean poslednje(Polaganje a, Polaganje b) {
        if (a.getTest().getDatum() == null) return false;
        if (b.getTest().getDatum() == null) return true;
        int c = a.getTest().getDatum().compareTo(b.getTest().getDatum());
        return c > 0 || (c == 0 && a.getTest().getId() > b.getTest().getId());
    }

    /** Rezultat jednog testa (404 dok nije objavljen); tačne vrednosti i rešenja samo uz objavu sa tačnim odgovorima. */
    @Transactional(readOnly = true)
    public RezultatTestaStudenta rezultat(long studentId, Long testId) {
        Test t = testRepository.findById(testId).orElseThrow(KontekstStudenta::nema);
        if (t.getObjavljeno() == null) throw KontekstStudenta.nema();
        Polaganje pol = polaganjeRepository.findByTestIdAndStudentId(t.getId(), studentId).orElseThrow(KontekstStudenta::nema);
        boolean tacni = t.isObjavaTacni();
        List<ZadatakRezultataStudenta> zadaci = new ArrayList<>();
        if (t.isOnline()) {
            TestPrimerak p = primerakRepository.findByTestIdAndStudentId(t.getId(), studentId).orElse(null);
            if (p != null && p.getStanje() == StanjePrimerka.PREDAT) {
                for (TestPrimerakZadatak z : zadatakRepository.findByPrimerakIdOrderByRb(p.getId())) {
                    SnimakZadatkaTesta sn = TestJson.snimak(z.getSnimak());
                    Map<String, Object> uneto = TestJson.uneto(z.getOdgovori());
                    Map<String, OcenaPoljaNastavnik> ocena = TestJson.ocena(z.getOcena());
                    List<PoljeRezultata> polja = new ArrayList<>();
                    for (Polje polje : sn.poljaZaUnos()) {
                        OcenaPoljaNastavnik o = ocena.get(polje.kljuc());
                        OdgovorPolja tacno = tacni ? sn.odgovori().get(polje.kljuc()) : null;
                        polja.add(new PoljeRezultata(polje.kljuc(), polje.oznaka(), polje.tip(), polje.opcije(), polje.jedinica(),
                                uneto == null ? null : uneto.get(polje.kljuc()), o == null ? 0 : o.udeo(), tacno));
                    }
                    Double poeni = z.getRucniPoeni() != null ? z.getRucniPoeni() : z.getPoeni();
                    zadaci.add(new ZadatakRezultataStudenta(z.getRb(), sn.stavka(), sn.svgId(), tacni ? sn.resenjeSvgId() : null,
                            poeni, z.getMaxPoena(), List.copyOf(polja)));
                }
            }
        }
        return new RezultatTestaStudenta(t.getId(), t.getTipTesta().getNaziv(), t.getPredmet().getNaziv(), t.getDatum(),
                pol.getOstvareniPoeni(), t.getMaxPoena() == null ? 0 : t.getMaxPoena(),
                Prolaz.polozenoIliNull(pol.getOstvareniPoeni(), t.getPragProlaza(), pol.getPrepisivao()), tacni, List.copyOf(zadaci));
    }

    // ---------------------------------------------------------------- pomoćno

    private record Kontekst(Test test, TestSmena smena, TestPrimerak primerak) {
    }

    /**
     * Primerak studenta pod ključem; test koji nije online, test u pripremi i test bez primerka studenta su isti 404.
     * Red testa se ne zaključava (redosled ključeva: nastavnik test pa primerak, student samo primerak).
     */
    private Kontekst zakljucaj(long studentId, Long testId) {
        Test t = testId == null ? null : testRepository.findById(testId).orElse(null);
        if (t == null || !t.isOnline()) throw KontekstStudenta.nema();
        TestPrimerak p = primerakRepository.zaTestIStudentaForUpdate(t.getId(), studentId).orElseThrow(KontekstStudenta::nema);
        TestSmena s = smenaRepository.findById(p.getSmenaId()).orElseThrow(KontekstStudenta::nema);
        if (s.getUlazOtvoren() == null) throw KontekstStudenta.nema();
        return new Kontekst(t, s, p);
    }

    /** Lenji istek sopstvenog primerka: rok + 10 s prošao, primerak radi → predaja sa sačuvanim ({@code ISTEK}). */
    private boolean istek(Kontekst k, LocalDateTime sada) {
        TestPrimerak p = k.primerak();
        if (p.getStanje() == StanjePrimerka.U_TOKU && k.smena().getPokrenuta() != null && k.smena().getZatvorena() == null
                && StanjeTesta.istekao(k.smena(), p, sada)) {
            online.predaj(k.test(), p, NacinPredaje.ISTEK, sada, ocenjivanje.ispravke(k.test().getId()));
            return true;
        }
        return false;
    }

    private void proveriIzmenu(Kontekst k, Long uredjajId) {
        TestPrimerak p = k.primerak();
        if (p.getStanje() == StanjePrimerka.PREDAT || p.getStanje() == StanjePrimerka.PONISTEN) {
            throw new SystemException(PREDAT, HttpStatus.CONFLICT);
        }
        if (k.smena().getZatvorena() != null) throw new SystemException(ISTEKLO, HttpStatus.CONFLICT);
        if (p.getStanje() != StanjePrimerka.U_TOKU || k.smena().getPokrenuta() == null) {
            throw new SystemException(NIJE_U_TOKU, HttpStatus.CONFLICT);
        }
        if (!Objects.equals(p.getAktivniUredjajId(), uredjajId)) throw new SystemException(DRUGI_UREDJAJ, HttpStatus.CONFLICT);
        if (StanjeTesta.istekao(k.smena(), p, LocalDateTime.now(clock))) throw new SystemException(ISTEKLO, HttpStatus.CONFLICT);
    }

    /**
     * Unos jednog zadatka: samo ključevi polja šeme; BROJ i TEKST tekst do {@value #MAX_TEKST} znakova, JEDAN_TACAN id
     * opcije, VISE_TACNIH lista različitih id-jeva opcija (čuva se sortirana); {@code null} i prazno brišu polje.
     */
    static Map<String, Object> proveriUnos(List<Polje> polja, Map<String, Object> uneto) {
        Map<String, Object> rezultat = new LinkedHashMap<>();
        if (uneto == null) return rezultat;
        Map<String, Polje> poKljucu = new HashMap<>();
        polja.forEach(p -> poKljucu.put(p.kljuc(), p));
        for (var e : uneto.entrySet()) {
            Polje p = poKljucu.get(e.getKey());
            if (p == null) throw OnlineTestService.bad("Neispravan format podataka.");
            Object v = e.getValue();
            if (v == null) continue;
            switch (p.tip()) {
                case BROJ, TEKST -> {
                    if (!(v instanceof String s)) throw OnlineTestService.bad("Neispravan format podataka.");
                    if (s.length() > MAX_TEKST) throw OnlineTestService.bad("Odgovor je predugačak.");
                    if (!s.isBlank()) rezultat.put(e.getKey(), s);
                }
                case JEDAN_TACAN -> {
                    Long id = ceo(v);
                    if (id == null || !opcije(p).contains(id)) throw OnlineTestService.bad("Neispravan format podataka.");
                    rezultat.put(e.getKey(), id);
                }
                case VISE_TACNIH -> {
                    if (!(v instanceof List<?> l)) throw OnlineTestService.bad("Neispravan format podataka.");
                    TreeSet<Long> ids = new TreeSet<>();
                    for (Object o : l) {
                        Long id = ceo(o);
                        if (id == null || !opcije(p).contains(id) || !ids.add(id)) {
                            throw OnlineTestService.bad("Neispravan format podataka.");
                        }
                    }
                    if (!ids.isEmpty()) rezultat.put(e.getKey(), new ArrayList<>(ids));
                }
            }
        }
        return rezultat;
    }

    private static Set<Long> opcije(Polje p) {
        Set<Long> s = new HashSet<>();
        if (p.opcije() != null) for (PoljeOpcija o : p.opcije()) if (o.id() != null) s.add(o.id());
        return s;
    }

    private static Long ceo(Object o) {
        if (o instanceof Integer i) return i.longValue();
        if (o instanceof Long l) return l;
        return null;
    }

    private PolaganjeStudenta stanje(Kontekst k, Long uredjajId) {
        Test t = k.test();
        TestSmena s = k.smena();
        TestPrimerak p = k.primerak();
        Student st = p.getStudent();
        FazaTesta faza = StanjeTesta.faza(s, p);
        List<ZadatakTesta> zadaci = null;
        if (faza == FazaTesta.U_TOKU) {
            zadaci = new ArrayList<>();
            for (TestPrimerakZadatak z : zadatakRepository.findByPrimerakIdOrderByRb(p.getId())) {
                SnimakZadatkaTesta sn = TestJson.snimak(z.getSnimak());
                Map<String, Double> poeniPolja = BodovanjeZadatka.poeniPoPolju(sn.poljaZaUnos(), z.getMaxPoena());
                List<PoljeTesta> polja = sn.poljaZaUnos().stream().map(x -> new PoljeTesta(x.kljuc(), x.oznaka(), x.tip(),
                        x.odstupanje(), x.odstupanjeTip(), x.jedinica(), x.zanemariVelicinu(), x.zanemariRazmake(), x.opcije(),
                        x.zanemariVodeceNule(), x.poeni(), x.konacno(), poeniPolja.getOrDefault(x.kljuc(), 0.0))).toList();
                zadaci.add(new ZadatakTesta(z.getId(), z.getRb(), sn.stavka(), z.getMaxPoena(), sn.svgId(), sn.rucno(), polja,
                        TestJson.uneto(z.getOdgovori()), z.getRev(), z.getSacuvano()));
            }
        }
        boolean racunar = uredjajId != null && uredjajRepository.findById(uredjajId)
                .map(u -> u.getNacin() == NacinPrijave.RACUNAR).orElse(false);
        LocalDateTime rok = StanjeTesta.rok(s, p);
        return new PolaganjeStudenta(t.getId(), t.getTipTesta().getNaziv(), t.getPredmet().getNaziv(), faza, st.getIme(),
                st.getPrezime(), st.getIndeks(), p.getGrupaTesta(), StanjeTesta.ms(rok, clock), clock.millis(),
                t.getMaxPoena() == null ? 0 : t.getMaxPoena(), zadaci == null ? null : List.copyOf(zadaci), p.getPredato(), racunar);
    }
}
