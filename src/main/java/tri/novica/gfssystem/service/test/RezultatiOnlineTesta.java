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
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.Test;
import tri.novica.gfssystem.entity.test.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.TestRepository;
import tri.novica.gfssystem.repository.test.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pregled online testa za nastavnika (spec 14, plan 4.4): rezultati, primerak studenta, ručni poeni, pregled po odgovorima,
 * ispravke ključa (sa vraćanjem i preračunom svih primeraka sa tom varijantom) i objava rezultata (svaki test, i papir).
 * Izmene posle „Završi evidentiranje“ (ispravke, ručni poeni, poništavanje) su dozvoljene: Novica ispravlja grešku i
 * posle pregleda, a polaganje se osvežava.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RezultatiOnlineTesta {

    private final OnlineTestService online;
    private final TestRepository testRepository;
    private final TestPrimerakRepository primerakRepository;
    private final TestPrimerakZadatakRepository zadatakRepository;
    private final TestDogadjajRepository dogadjajRepository;
    private final TestIspravkaRepository ispravkaRepository;
    private final TestStavkaRepository stavkaRepository;
    private final OcenjivanjeOnlineTesta ocenjivanje;
    private final Clock clock;

    public RezultatiTesta rezultati(Long testId) {
        Test test = online.zakljucaj(testId);
        TestSmena smena = online.smena(test);
        online.obradiIstek(test, smena);
        List<TestPrimerak> primerci = primerakRepository.zaTest(test.getId());
        Map<Long, List<TestPrimerakZadatak>> zadaci = zadatakRepository
                .zaPrimerke(primerci.stream().map(TestPrimerak::getId).toList()).stream()
                .collect(Collectors.groupingBy(TestPrimerakZadatak::getPrimerakId));
        KonzolaTesta konzola = online.konzola(test, smena);
        Map<Long, StudentKonzole> znaci = konzola.studenti().stream()
                .collect(Collectors.toMap(StudentKonzole::primerakId, s -> s));

        Map<Integer, String> nazivRb = new TreeMap<>();
        Map<Integer, Double> maxRb = new HashMap<>();
        Map<Integer, List<Double>> poeniRb = new HashMap<>();
        Map<Integer, List<Double>> tacnostRb = new HashMap<>();
        List<StudentRezultata> studenti = new ArrayList<>();
        for (TestPrimerak p : primerci) {
            Student s = p.getStudent();
            List<ZadatakRezultata> zr = new ArrayList<>();
            for (TestPrimerakZadatak z : zadaci.getOrDefault(p.getId(), List.of())) {
                SnimakZadatkaTesta sn = TestJson.snimak(z.getSnimak());
                boolean ocenjen = p.getStanje() == StanjePrimerka.PREDAT;
                Double poeni = ocenjen ? (z.getRucniPoeni() != null ? z.getRucniPoeni() : z.getPoeni()) : null;
                zr.add(new ZadatakRezultata(z.getRb(), poeni, z.getMaxPoena(), sn.rucno()));
                nazivRb.putIfAbsent(z.getRb(), sn.stavka());
                maxRb.putIfAbsent(z.getRb(), z.getMaxPoena());
                if (poeni != null) {
                    poeniRb.computeIfAbsent(z.getRb(), x -> new ArrayList<>()).add(poeni);
                    if (z.getMaxPoena() > 0) tacnostRb.computeIfAbsent(z.getRb(), x -> new ArrayList<>()).add(poeni / z.getMaxPoena());
                }
            }
            StudentKonzole k = znaci.get(p.getId());
            List<String> z = new ArrayList<>();
            if (k != null) {
                if (k.promenaUredjaja() > 0) z.add("Promena uređaja: " + k.promenaUredjaja());
                if (k.napustanja() > 0) z.add("Napuštao prozor: " + k.napustanja() + " (" + k.napustanjaSekundi() + " s)");
                if (k.drugaMreza()) z.add("Druga mreža");
                if (p.getProduzenjeMin() > 0) z.add("Produženo " + p.getProduzenjeMin() + " min");
            }
            studenti.add(new StudentRezultata(p.getId(), s.getId(), s.getIme(), s.getPrezime(), s.getIndeks(), p.getGrupaTesta(),
                    StanjeTesta.konzola(smena, p), p.getStanje() == StanjePrimerka.PREDAT ? p.getPoeni() : null, List.copyOf(zr),
                    p.isPrepisivao(), List.copyOf(z)));
        }
        List<StavkaRezultata> stavke = new ArrayList<>();
        nazivRb.forEach((rb, naziv) -> {
            List<Double> l = poeniRb.getOrDefault(rb, List.of());
            List<Double> t = tacnostRb.getOrDefault(rb, List.of());
            stavke.add(new StavkaRezultata(rb, naziv, maxRb.get(rb),
                    l.isEmpty() ? null : BodovanjeZadatka.zaokruzi(l.stream().mapToDouble(d -> d).average().orElse(0)),
                    t.isEmpty() ? null : BodovanjeZadatka.zaokruzi(t.stream().mapToDouble(d -> d).average().orElse(0))));
        });
        return new RezultatiTesta(test.getId(), test.getMaxPoena() == null ? 0 : test.getMaxPoena(), test.getPragProlaza(),
                List.copyOf(stavke), List.copyOf(studenti));
    }

    public PrimerakNastavnika primerak(Long testId, Long primerakId) {
        Test test = online.zakljucaj(testId);
        TestSmena smena = online.smena(test);
        online.obradiIstek(test, smena);
        TestPrimerak p = primerakRepository.findById(primerakId)
                .filter(x -> x.getTestId().equals(test.getId()))
                .orElseThrow(() -> new SystemException(OnlineTestService.NEMA_PRIMERKA, HttpStatus.NOT_FOUND));
        return primerakDto(test, smena, p);
    }

    public PrimerakNastavnika rucniPoeni(Long testId, Long zadatakId, RucniPoeniCmd cmd) {
        Test test = online.zakljucaj(testId);
        TestSmena smena = online.smena(test);
        online.obradiIstek(test, smena);
        TestPrimerakZadatak z = zadatakRepository.findById(zadatakId)
                .orElseThrow(() -> new SystemException("Zadatak ne postoji.", HttpStatus.NOT_FOUND));
        TestPrimerak p = primerakRepository.findByIdForUpdate(z.getPrimerakId())
                .filter(x -> x.getTestId().equals(test.getId()))
                .orElseThrow(() -> new SystemException("Zadatak ne postoji.", HttpStatus.NOT_FOUND));
        if (p.getStanje() != StanjePrimerka.PREDAT) throw OnlineTestService.conflict("Ručni poeni se upisuju posle predaje.");
        Double poeni = cmd == null ? null : cmd.poeni();
        if (poeni != null) {
            if (!Double.isFinite(poeni) || poeni < 0 || poeni > z.getMaxPoena() + 1e-9) {
                throw OnlineTestService.bad("Poeni moraju biti između 0 i " + format(z.getMaxPoena()) + ".");
            }
            if (Math.abs(poeni * 100 - Math.rint(poeni * 100)) > 1e-6) {
                throw OnlineTestService.bad("Poeni imaju najviše dve decimale.");
            }
        }
        String napomena = cmd == null || cmd.napomena() == null || cmd.napomena().isBlank() ? null : cmd.napomena().strip();
        if (napomena != null && napomena.length() > 500) throw OnlineTestService.bad("Napomena je predugačka.");
        List<TestPrimerakZadatak> zadaci = zadatakRepository.zaPrimerakForUpdate(p.getId());
        TestPrimerakZadatak cilj = zadaci.stream().filter(x -> x.getId().equals(z.getId())).findFirst().orElseThrow();
        cilj.setRucniPoeni(poeni == null ? null : BodovanjeZadatka.zaokruzi(poeni));
        cilj.setNapomena(napomena);
        zadatakRepository.saveAll(zadaci);
        ocenjivanje.upisiZbir(test, p, zadaci);
        primerakRepository.save(p);
        return primerakDto(test, smena, p);
    }

    public OdgovoriTesta odgovori(Long testId) {
        Test test = online.zakljucaj(testId);
        online.obradiIstek(test, online.smena(test));
        return odgovoriDto(test);
    }

    /** Nova ispravka ključa: odmah preračuna sve predate primerke testa sa tom varijantom. */
    public OdgovoriTesta ispravi(Long testId, IspravkaCmd cmd) {
        Test test = online.zakljucaj(testId);
        if (cmd == null || cmd.varijantaId() == null || cmd.polje() == null || cmd.vrsta() == null) {
            throw OnlineTestService.bad("Varijanta, polje i vrsta su obavezni.");
        }
        List<TestPrimerak> primerci = primerakRepository.zaTestForUpdate(test.getId());
        List<TestPrimerakZadatak> zadaci = zadatakRepository.zaPrimerke(primerci.stream().map(TestPrimerak::getId).toList());
        SnimakZadatkaTesta snimak = zadaci.stream().filter(z -> z.getVarijantaId().equals(cmd.varijantaId())).findFirst()
                .map(z -> TestJson.snimak(z.getSnimak()))
                .orElseThrow(() -> new SystemException("Varijanta nije na testu.", HttpStatus.NOT_FOUND));
        Polje polje = snimak.polja().stream().filter(p -> p.kljuc().equals(cmd.polje())).findFirst()
                .orElseThrow(() -> OnlineTestService.bad("Polje ne postoji u varijanti."));
        String vrednost;
        switch (cmd.vrsta()) {
            case PONISTI -> vrednost = null;
            case PRIHVATI -> {
                if (cmd.vrednost() == null || cmd.vrednost().isBlank() || cmd.vrednost().length() > 400) {
                    throw OnlineTestService.bad("Odgovor za prihvatanje je obavezan.");
                }
                vrednost = cmd.vrednost();
            }
            case KLJUC -> {
                OdgovorPolja o = TestJson.odgovorPolja(cmd.vrednost());
                proveriKljuc(polje, o);
                vrednost = TestJson.uJson(o);
            }
            default -> throw OnlineTestService.bad("Nepoznata vrsta ispravke.");
        }
        TestIspravka i = new TestIspravka();
        i.setTestId(test.getId());
        i.setVarijantaId(cmd.varijantaId());
        i.setPolje(cmd.polje());
        i.setVrsta(cmd.vrsta());
        i.setVrednost(vrednost);
        i.setKreirano(LocalDateTime.now(clock));
        ispravkaRepository.save(i);
        ispravkaRepository.flush();
        preracunaj(test, primerci, zadaci, cmd.varijantaId());
        log.info("Ispravka ključa: test={}, varijanta={}, polje={}, vrsta={}", test.getId(), cmd.varijantaId(), cmd.polje(), cmd.vrsta());
        return odgovoriDto(test);
    }

    /** Vraćanje ispravke: red ostaje sa {@code vracena}, primerci se preračunavaju. */
    public OdgovoriTesta vratiIspravku(Long testId, Long ispravkaId) {
        Test test = online.zakljucaj(testId);
        TestIspravka i = ispravkaRepository.findByIdAndTestId(ispravkaId, test.getId())
                .orElseThrow(() -> new SystemException("Ispravka ne postoji.", HttpStatus.NOT_FOUND));
        if (i.getVracena() != null) throw OnlineTestService.conflict("Ispravka je već vraćena.");
        i.setVracena(LocalDateTime.now(clock));
        ispravkaRepository.saveAndFlush(i);
        List<TestPrimerak> primerci = primerakRepository.zaTestForUpdate(test.getId());
        List<TestPrimerakZadatak> zadaci = zadatakRepository.zaPrimerke(primerci.stream().map(TestPrimerak::getId).toList());
        preracunaj(test, primerci, zadaci, i.getVarijantaId());
        return odgovoriDto(test);
    }

    /** Objava rezultata (svaki test, i papir; online tek kad je završen). */
    public void objavi(Long testId, ObjavaCmd cmd) {
        Test test = testRepository.findByIdForUpdate(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, HttpStatus.NOT_FOUND));
        if (test.isOnline()) {
            TestSmena smena = online.smena(test);
            online.obradiIstek(test, smena);
            if (StanjeTesta.stanje(smena) != StanjeOnline.ZAVRSEN) throw OnlineTestService.conflict("Test još nije završen.");
        }
        test.setObjavljeno(LocalDateTime.now(clock));
        test.setObjavaTacni(cmd != null && Boolean.TRUE.equals(cmd.tacni()));
        testRepository.save(test);
        log.info("Rezultati testa objavljeni: test={}, tacni={}", test.getId(), test.isObjavaTacni());
    }

    public void povuciObjavu(Long testId) {
        Test test = testRepository.findByIdForUpdate(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, HttpStatus.NOT_FOUND));
        test.setObjavljeno(null);
        test.setObjavaTacni(false);
        testRepository.save(test);
    }

    // ---------------------------------------------------------------- pomoćno

    private void preracunaj(Test test, List<TestPrimerak> primerci, List<TestPrimerakZadatak> zadaci, Long varijantaId) {
        Map<Long, BodovanjeZadatka.Ispravke> isp = ocenjivanje.ispravke(test.getId());
        Set<Long> pogodjeni = zadaci.stream().filter(z -> z.getVarijantaId().equals(varijantaId))
                .map(TestPrimerakZadatak::getPrimerakId).collect(Collectors.toSet());
        for (TestPrimerak p : primerci) {
            if (!pogodjeni.contains(p.getId())) continue;
            if (p.getStanje() == StanjePrimerka.PREDAT) {
                ocenjivanje.oceni(test, p, isp);
                primerakRepository.save(p);
            }
        }
    }

    private static void proveriKljuc(Polje polje, OdgovorPolja o) {
        switch (polje.tip()) {
            case BROJ -> {
                if (o.broj() == null || !Double.isFinite(o.broj())) throw OnlineTestService.bad("Ključ broja je obavezan.");
            }
            case TEKST -> {
                if (o.prihvatljivi() == null || o.prihvatljivi().isEmpty() || o.prihvatljivi().stream().anyMatch(s -> s == null || s.isBlank())) {
                    throw OnlineTestService.bad("Ključ teksta je obavezan.");
                }
            }
            case JEDAN_TACAN -> {
                Set<Long> ids = opcije(polje);
                if (o.tacna() == null || !ids.contains(o.tacna())) throw OnlineTestService.bad("Tačna opcija ne postoji.");
            }
            case VISE_TACNIH -> {
                Set<Long> ids = opcije(polje);
                if (o.tacne() == null || o.tacne().isEmpty() || !ids.containsAll(o.tacne())) {
                    throw OnlineTestService.bad("Tačne opcije ne postoje.");
                }
            }
        }
    }

    private static Set<Long> opcije(Polje p) {
        return p.opcije() == null ? Set.of() : p.opcije().stream().map(PoljeOpcija::id).collect(Collectors.toSet());
    }

    private PrimerakNastavnika primerakDto(Test test, TestSmena smena, TestPrimerak p) {
        Student s = p.getStudent();
        List<ZadatakPrimerka> zadaci = new ArrayList<>();
        for (TestPrimerakZadatak z : zadatakRepository.findByPrimerakIdOrderByRb(p.getId())) {
            SnimakZadatkaTesta sn = TestJson.snimak(z.getSnimak());
            Double poeni = z.getRucniPoeni() != null ? z.getRucniPoeni() : z.getPoeni();
            zadaci.add(new ZadatakPrimerka(z.getId(), z.getRb(), sn.stavka(), sn.tip(), sn.nivo(), z.getVarijantaId(), sn.svgId(),
                    sn.resenjeSvgId(), sn.polja(), sn.odgovori(), TestJson.uneto(z.getOdgovori()), TestJson.ocena(z.getOcena()),
                    z.getMaxPoena(), z.getPoeni(), z.getRucniPoeni(), p.getStanje() == StanjePrimerka.PREDAT ? poeni : null,
                    z.getNapomena(), z.getSacuvano()));
        }
        List<DogadjajTesta> dogadjaji = dogadjajRepository.findByPrimerakIdOrderByVremeAscIdAsc(p.getId()).stream()
                .map(d -> new DogadjajTesta(d.getVreme(), d.getVrsta(), d.getDetalj())).toList();
        return new PrimerakNastavnika(p.getId(), s.getId(), s.getIme(), s.getPrezime(), s.getIndeks(), p.getGrupaTesta(),
                StanjeTesta.konzola(smena, p), p.getUsao(), p.getPredato(),
                p.getNacinPredaje() == null ? null : p.getNacinPredaje().name(),
                p.getStanje() == StanjePrimerka.PREDAT ? p.getPoeni() : null, test.getMaxPoena() == null ? 0 : test.getMaxPoena(),
                List.copyOf(zadaci), dogadjaji);
    }

    private OdgovoriTesta odgovoriDto(Test test) {
        List<TestPrimerak> primerci = primerakRepository.zaTest(test.getId());
        Map<Long, TestPrimerak> poId = primerci.stream().collect(Collectors.toMap(TestPrimerak::getId, p -> p));
        List<TestPrimerakZadatak> zadaci = zadatakRepository.zaPrimerke(poId.keySet());
        List<TestIspravka> vazece = ispravkaRepository.findByTestIdAndVracenaIsNullOrderByIdAsc(test.getId());
        Map<Long, BodovanjeZadatka.Ispravke> isp = OcenjivanjeOnlineTesta.ispravke(vazece);
        Map<Long, Integer> rbStavke = stavkaRepository.findByTestIdOrderByRb(test.getId()).stream()
                .collect(Collectors.toMap(TestStavka::getId, TestStavka::getRb));

        Map<Long, List<TestPrimerakZadatak>> poVarijanti = new LinkedHashMap<>();
        for (TestPrimerakZadatak z : zadaci) {
            TestPrimerak p = poId.get(z.getPrimerakId());
            if (p == null || p.getStanje() == StanjePrimerka.PONISTEN) continue;
            poVarijanti.computeIfAbsent(z.getVarijantaId(), x -> new ArrayList<>()).add(z);
        }
        List<VarijantaOdgovora> varijante = new ArrayList<>();
        for (var e : poVarijanti.entrySet()) {
            List<TestPrimerakZadatak> l = e.getValue();
            SnimakZadatkaTesta sn = TestJson.snimak(l.get(0).getSnimak());
            BodovanjeZadatka.Ispravke iv = isp.getOrDefault(e.getKey(), BodovanjeZadatka.Ispravke.NEMA);
            List<TestPrimerakZadatak> predati = l.stream()
                    .filter(z -> poId.get(z.getPrimerakId()).getStanje() == StanjePrimerka.PREDAT).toList();
            List<PoljeOdgovora> polja = new ArrayList<>();
            for (Polje polje : sn.poljaZaUnos()) {
                OdgovorPolja tacno = iv.kljucevi().getOrDefault(polje.kljuc(), sn.odgovori().get(polje.kljuc()));
                Set<String> prihvaceni = iv.prihvaceni().getOrDefault(polje.kljuc(), Set.of());
                Map<String, int[]> broj = new LinkedHashMap<>();
                Map<String, String> prikaz = new HashMap<>();
                Map<String, Object> primer = new HashMap<>();
                int prazno = 0;
                for (TestPrimerakZadatak z : predati) {
                    Map<String, Object> uneto = TestJson.uneto(z.getOdgovori());
                    Object dato = uneto == null ? null : uneto.get(polje.kljuc());
                    String kljuc = BodovanjeZadatka.kljucOdgovora(polje, dato);
                    if (kljuc == null) {
                        prazno++;
                        continue;
                    }
                    broj.computeIfAbsent(kljuc, k -> new int[1])[0]++;
                    prikaz.putIfAbsent(kljuc, prikaz(polje, dato));
                    primer.putIfAbsent(kljuc, dato);
                }
                List<GrupaOdgovora> grupe = new ArrayList<>();
                broj.forEach((k, b) -> {
                    boolean prihvacen = prihvaceni.contains(k);
                    boolean tacan = BodovanjeZadatka.udeo(polje, tacno, primer.get(k)) >= 1 - 1e-9;
                    grupe.add(new GrupaOdgovora(k, prikaz.get(k), b[0], tacan || prihvacen, prihvacen && !tacan));
                });
                grupe.sort(Comparator.comparingInt(GrupaOdgovora::broj).reversed().thenComparing(GrupaOdgovora::kljuc));
                polja.add(new PoljeOdgovora(polje.kljuc(), polje.oznaka(), polje.tip(), BodovanjeZadatka.poeniPolja(polje),
                        Boolean.TRUE.equals(polje.konacno()), polje.opcije(), tacno,
                        OcenjivanjeOnlineTesta.ponisteno(iv, polje.kljuc()), prazno, List.copyOf(grupe)));
            }
            List<String> grupeTesta = l.stream().map(z -> poId.get(z.getPrimerakId()).getGrupaTesta()).filter(Objects::nonNull)
                    .map(Enum::name).distinct().sorted().toList();
            varijante.add(new VarijantaOdgovora(e.getKey(), rbStavke.getOrDefault(l.get(0).getStavkaId(), 0), sn.naziv(), sn.tip(),
                    sn.nivo(), grupeTesta, sn.svgId(), sn.resenjeSvgId(), l.size(), List.copyOf(polja)));
        }
        varijante.sort(Comparator.comparingInt(VarijantaOdgovora::stavkaRb).thenComparingLong(VarijantaOdgovora::varijantaId));
        List<IspravkaTesta> ispravke = vazece.stream()
                .map(i -> new IspravkaTesta(i.getId(), i.getVarijantaId(), i.getPolje(), i.getVrsta(), i.getVrednost(), i.getKreirano()))
                .toList();
        return new OdgovoriTesta(List.copyOf(varijante), ispravke);
    }

    /** Prikaz odgovora: tekst kako je upisan (prvi u grupi), opcije po tekstu. */
    private static String prikaz(Polje p, Object dato) {
        return switch (p.tip()) {
            case BROJ, TEKST -> dato instanceof String s ? s.strip() : String.valueOf(dato);
            case JEDAN_TACAN -> tekstOpcije(p, dato);
            case VISE_TACNIH -> dato instanceof List<?> l
                    ? l.stream().map(o -> tekstOpcije(p, o)).sorted().collect(Collectors.joining(", ")) : String.valueOf(dato);
        };
    }

    private static String tekstOpcije(Polje p, Object id) {
        if (p.opcije() != null && id instanceof Number n) {
            for (PoljeOpcija o : p.opcije()) {
                if (o.id() != null && o.id() == n.longValue()) return o.tekst();
            }
        }
        return String.valueOf(id);
    }

    private static String format(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
