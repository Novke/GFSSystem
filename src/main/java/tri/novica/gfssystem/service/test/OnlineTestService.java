package tri.novica.gfssystem.service.test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.test.online.*;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.entity.banka.Varijanta;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.StudentskiUredjaj;
import tri.novica.gfssystem.entity.test.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.TestRepository;
import tri.novica.gfssystem.repository.TipTestaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.prijava.StudentskiUredjajRepository;
import tri.novica.gfssystem.repository.test.*;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;
import tri.novica.gfssystem.service.test.IzvlacenjeTesta.Kandidat;
import tri.novica.gfssystem.service.test.IzvlacenjeTesta.Mesto;
import tri.novica.gfssystem.utility.Nivo;
import tri.novica.gfssystem.utility.SkolskaGodina;
import tri.novica.gfssystem.utility.Utility;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

/**
 * Online test za nastavnika (spec 8, 13; plan 4.2 i 4.3): pravljenje iz šablona, priprema (izvlačenje, studenti), tok
 * (ulaz, pokretanje, završetak, produženje, vraćanje, poništavanje) i konzola.
 * <p>
 * <b>Ključevi:</b> svaka izmena prvo zaključa red testa ({@code TestRepository.findByIdForUpdate}), pa primerke po id-ju,
 * pa varijante (izvlačenje); studentske izmene zaključavaju samo red primerka, pa ciklusa nema. Vreme ide kroz
 * {@link Clock}, slučajnost kroz {@link Random} bean (seme testa za prvo izvlačenje).
 * <p>
 * <b>Istek:</b> primerak čiji je rok (zajednički rok smene + produženje + 10 s) prošao zaključava se sa onim što je
 * sačuvano ({@code ISTEK}), a smena se zatvara kad je rok prošao svima; to radi zakazani posao ({@link IstekTestova}) i
 * lenjo svako čitanje nastavnika ({@link #obradiIstek}).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class OnlineTestService {

    public static final String NIJE_ONLINE = "Test nije online.";
    public static final String SAMO_PRIPREMA = "Test više nije u pripremi.";
    public static final String NEMA_KANDIDATA = "nema kandidata u banci.";
    public static final String BANKA_SE_MENJA = "Banka se upravo menja; pokušaj ponovo.";
    public static final String TEST_ZAVRSEN = "Test je završen.";
    public static final String NEMA_PRIMERKA = "Primerak ne postoji.";

    private static final SecureRandom KOD = new SecureRandom();

    private final TestRepository testRepository;
    private final TipTestaRepository tipTestaRepository;
    private final GrupaRepository grupaRepository;
    private final StudentRepository studentRepository;
    private final TestStavkaRepository stavkaRepository;
    private final TestSmenaRepository smenaRepository;
    private final TestIzborRepository izborRepository;
    private final TestPrimerakRepository primerakRepository;
    private final TestPrimerakZadatakRepository zadatakRepository;
    private final TestDogadjajRepository dogadjajRepository;
    private final VarijantaRepository varijantaRepository;
    private final StudentskiUredjajRepository uredjajRepository;
    private final SablonTestaService sablonService;
    private final KandidatiTesta kandidatiTesta;
    private final OcenjivanjeOnlineTesta ocenjivanje;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final Random random;

    // ================================================================ priprema

    /** Ko može da polaže: studenti grupe i stariji sa aktivnošću ili čekiranjem ove školske godine na predmetu sa grupom, ili sa oznakom „stariji“. */
    @Transactional(readOnly = true)
    public KandidatiStudenti kandidatiStudenti(Long grupaId, Long predmetId) {
        Grupa g = grupaRepository.findById(grupaId)
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + grupaId, HttpStatus.NOT_FOUND));
        int godina = SkolskaGodina.tekuca(clock);
        MapSqlParameterSource p = new MapSqlParameterSource("g", grupaId)
                .addValue("predmet", predmetId == null ? -1L : predmetId)
                .addValue("od", SkolskaGodina.pocetak(godina)).addValue("do", SkolskaGodina.kraj(godina))
                .addValue("godinaUpisa", g.getGodinaUpisa() == null ? -1 : g.getGodinaUpisa());
        List<StudentKratko> grupa = jdbc.query("""
                SELECT s.id, s.ime, s.prezime, s.indeks, s.godina, s.stariji FROM studenti s
                WHERE s.grupa_id = :g ORDER BY s.prezime, s.ime, s.id""", p,
                (rs, i) -> new StudentKratko(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getBoolean(6)));
        List<StudentKratko> stariji = jdbc.query("""
                SELECT s.id, s.ime, s.prezime, s.indeks, s.godina FROM studenti s JOIN grupe sg ON sg.id = s.grupa_id
                WHERE s.grupa_id <> :g AND sg.godina_upisa < :godinaUpisa AND (
                      s.stariji = TRUE
                   OR EXISTS (SELECT 1 FROM aktivnosti a JOIN predavanja pr ON pr.id = a.predavanje_id
                              WHERE a.student_id = s.id AND pr.grupa_id = :g AND pr.predmet_id = :predmet
                                AND pr.datum BETWEEN :od AND :do)
                   OR EXISTS (SELECT 1 FROM cekiranja c JOIN predavanja pr ON pr.id = c.predavanje_id
                              WHERE c.student_id = s.id AND pr.grupa_id = :g AND pr.predmet_id = :predmet
                                AND pr.datum BETWEEN :od AND :do))
                ORDER BY s.prezime, s.ime, s.id""", p,
                (rs, i) -> new StudentKratko(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), true));
        return new KandidatiStudenti(grupa, stariji);
    }

    /** „Generiši“: test u pripremi sa prepisom stavki šablona, jednom smenom, izvučenim zadacima i primercima. */
    public OnlineTest kreiraj(NoviOnlineTestCmd cmd) {
        if (cmd == null || cmd.sablonId() == null) throw bad("Šablon je obavezan.");
        SablonTesta sablon = sablonService.ucitaj(cmd.sablonId());
        if (sablon.isArhiviran()) throw bad("Šablon je arhiviran.");
        Grupa grupa = grupaRepository.findById(cmd.grupaId() == null ? -1 : cmd.grupaId())
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + cmd.grupaId(), HttpStatus.NOT_FOUND));
        TipTesta tip = tipTestaRepository.findById(cmd.tipTestaId() == null ? -1 : cmd.tipTestaId())
                .orElseThrow(() -> new SystemException("Tip testa ne postoji! ID = " + cmd.tipTestaId(), HttpStatus.NOT_FOUND));
        Predmet predmet = sablon.getPredmet();
        if (!tip.getPredmet().getId().equals(predmet.getId())) throw bad("Tip testa ne pripada predmetu šablona.");
        if (!Boolean.TRUE.equals(tip.getAktivan())) throw bad("Tip testa nije aktivan!");
        if (cmd.datum() == null) throw bad("Datum je obavezan.");
        int trajanje = cmd.trajanjeMin() == null ? sablon.getTrajanjeMin() : cmd.trajanjeMin();
        if (trajanje < 5 || trajanje > 300) throw bad("Trajanje mora biti od 5 do 300 minuta.");
        RezimTesta rezim = cmd.rezim() == null ? sablon.getRezim() : cmd.rezim();
        int brojGrupa = rezim == RezimTesta.GRUPE ? (cmd.brojGrupa() == null ? sablon.getBrojGrupa() : cmd.brojGrupa()) : 1;
        if (brojGrupa < 1 || brojGrupa > 4) throw bad("Broj grupa testa mora biti od 1 do 4.");
        LinkedHashSet<Long> studentIds = new LinkedHashSet<>(cmd.studentIds() == null ? List.of() : cmd.studentIds());
        if (studentIds.contains(null) || studentIds.isEmpty() || studentIds.size() > 300) {
            throw bad("Test polaže od 1 do 300 studenata.");
        }
        List<Student> studenti = studentRepository.findAllById(studentIds);
        if (studenti.size() != studentIds.size()) throw new SystemException("Student ne postoji.", HttpStatus.NOT_FOUND);
        for (Student s : studenti) {
            if (!Utility.smeNaNastavuGrupe(s, grupa)) {
                throw bad("Student " + s.getIndeks() + " ne pripada grupi " + grupa.getNaziv());
            }
        }
        int max = SablonTestaService.maxPoena(sablon);
        if (sablon.getPragProlaza() != null && sablon.getPragProlaza() > max) {
            throw bad("Prag prolaza mora biti između 0 i maksimalnog broja poena.");
        }

        Test t = new Test();
        t.setPredmet(predmet);
        t.setGrupa(grupa);
        t.setTipTesta(tip);
        t.setDatum(cmd.datum());
        t.setMaxPoena(max);
        t.setPragProlaza(sablon.getPragProlaza());
        t.setPregledan(false);
        t.setNacin(NacinTesta.ONLINE);
        t.setSablonId(sablon.getId());
        t.setRezim(rezim);
        t.setTrajanjeMin(trajanje);
        t.setSeme(random.nextLong());
        if (rezim == RezimTesta.GRUPE) t.generisiGrupe(brojGrupa);
        else t.setGrupe(new LinkedHashSet<>());
        t = testRepository.saveAndFlush(t);
        Test test = testRepository.findByIdForUpdate(t.getId()).orElseThrow();

        for (SablonStavka ss : sablon.getStavke()) {
            TestStavka st = new TestStavka();
            st.setTestId(test.getId());
            st.setRb(ss.getRb());
            st.setNaziv(ss.getNaziv());
            st.setPoeni(ss.getPoeni());
            st.setBroj(ss.getBroj());
            st.setNivoi(ss.getNivoi());
            st.setIstiTip(ss.isIstiTip());
            st.setRucno(ss.isRucno());
            st.setIzvori(TestJson.uJson(new ArrayList<>(ss.getIzvori())));
            stavkaRepository.save(st);
        }
        TestSmena smena = new TestSmena();
        smena.setTestId(test.getId());
        smena.setRb(1);
        smenaRepository.save(smena);
        stavkaRepository.flush();

        // studenti redom kao u spisku grupe (prezime, ime), da raspodela po semenu ne zavisi od redosleda u zahtevu
        List<Student> redom = new ArrayList<>(studenti);
        redom.sort(Comparator.comparing(Student::getPrezime, Comparator.nullsLast(String::compareTo))
                .thenComparing(Student::getIme, Comparator.nullsLast(String::compareTo)).thenComparing(Student::getId));
        generisi(test, smena, redom, new Random(test.getSeme()));
        log.info("Online test napravljen: id={}, sablon={}, studenata={}", test.getId(), sablon.getId(), redom.size());
        return online(test);
    }

    /**
     * Prvo izvlačenje: grupe testa (GRUPE), plan po stavci i primerci sa snimcima. Stavka bez kandidata je 409.
     */
    private void generisi(Test test, TestSmena smena, List<Student> studenti, Random r) {
        List<TestStavka> stavke = stavkaRepository.findByTestIdOrderByRb(test.getId());
        StabloPredavanja stablo = kandidatiTesta.stablo(test.getPredmet().getId());
        List<Long> ids = studenti.stream().map(Student::getId).toList();
        Map<Long, Set<Long>> istorija = istorija(test, ids);
        Set<Long> objavljena = new HashSet<>(zadatakRepository.objavljenaResenja(test.getPredmet().getId(), test.getId()));
        boolean grupe = test.getRezim() == RezimTesta.GRUPE;
        List<TestGrupa> grupeTesta = grupeTesta(test);
        Map<Long, TestGrupa> grupaStudenta = grupe ? IzvlacenjeTesta.uGrupe(ids, grupeTesta, r) : Map.of();
        List<Object> jedinice = grupe ? new ArrayList<>(grupeTesta) : new ArrayList<>(ids);
        ToIntBiFunction<Object, Long> kazna = kazna(grupe, grupaStudenta, istorija, objavljena);

        Map<Object, Set<Long>> zauzeto = new HashMap<>();
        Map<Long, IzvlacenjeTesta.PlanStavke<Object>> planovi = new LinkedHashMap<>();
        for (TestStavka st : stavke) {
            KandidatiTesta.OpisStavke opis = opis(stablo, st, test);
            int potrebno = (grupe ? grupeTesta.size() : 1) * st.getBroj();
            var plan = IzvlacenjeTesta.planiraj(opis.stavka(), st.getIzabraniNivo(), potrebno, jedinice, Map.of(),
                    zauzeto, kazna, r);
            if (plan.nivo() == null || plan.izbor().size() < jedinice.size() * st.getBroj()) {
                throw new SystemException("Stavka „" + st.getNaziv() + "“ " + NEMA_KANDIDATA, HttpStatus.CONFLICT);
            }
            st.setIzabraniNivo(plan.nivo());
            planovi.put(st.getId(), plan);
            for (int m = 0; m < st.getBroj(); m++) {
                if (grupe) {
                    for (TestGrupa g : grupeTesta) {
                        Kandidat k = plan.izbor().get(new Mesto<>(g, m));
                        izborRepository.save(izbor(test, smena, g, st, m, k.tipId(), k.varijantaId()));
                    }
                } else {
                    izborRepository.save(izbor(test, smena, null, st, m, plan.tipovi().get(m), null));
                }
            }
        }
        stavkaRepository.saveAll(stavke);
        Map<Long, Varijanta> varijante = zakljucajIUcitaj(planovi.values().stream()
                .flatMap(p -> p.izbor().values().stream()).map(Kandidat::varijantaId).collect(Collectors.toSet()));

        for (Student s : studenti) {
            TestPrimerak p = new TestPrimerak();
            p.setTestId(test.getId());
            p.setStudent(s);
            p.setSmenaId(smena.getId());
            p.setGrupaTesta(grupe ? grupaStudenta.get(s.getId()) : null);
            p = primerakRepository.save(p);
            Object j = grupe ? grupaStudenta.get(s.getId()) : s.getId();
            int rb = 1;
            for (TestStavka st : stavke) {
                var plan = planovi.get(st.getId());
                for (int m = 0; m < st.getBroj(); m++) {
                    Kandidat k = plan.izbor().get(new Mesto<>(j, m));
                    zadatakRepository.save(zadatak(p, rb++, st, m, varijante.get(k.varijantaId())));
                }
            }
        }
        zadatakRepository.flush();
    }

    /** „Izvuci ponovo“, „Izaberi tip“, „Izaberi nivo“, „Izaberi varijantu“ (plan 4.2), samo u pripremi. */
    public OnlineTest izvuci(Long testId, IzvuciCmd cmd) {
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        if (StanjeTesta.stanje(smena) != StanjeOnline.PRIPREMA) throw conflict(SAMO_PRIPREMA);
        if (cmd == null || cmd.stavkaId() == null) throw bad("Stavka je obavezna.");
        List<TestStavka> stavke = stavkaRepository.findByTestIdOrderByRb(test.getId());
        TestStavka st = stavke.stream().filter(s -> s.getId().equals(cmd.stavkaId())).findFirst()
                .orElseThrow(() -> new SystemException("Stavka ne postoji.", HttpStatus.NOT_FOUND));
        boolean grupe = test.getRezim() == RezimTesta.GRUPE;
        List<TestGrupa> grupeTesta = grupeTesta(test);
        if (cmd.grupaTesta() != null && (!grupe || !grupeTesta.contains(cmd.grupaTesta()))) {
            throw bad("Grupa testa ne postoji u testu.");
        }
        if (cmd.mesto() != null && (cmd.mesto() < 0 || cmd.mesto() >= st.getBroj())) throw bad("Mesto ne postoji u stavci.");
        StabloPredavanja stablo = kandidatiTesta.stablo(test.getPredmet().getId());
        KandidatiTesta.OpisStavke opis = opis(stablo, st, test);

        List<TestPrimerak> primerci = primerakRepository.zaTestForUpdate(test.getId());
        Map<Long, List<TestPrimerakZadatak>> zadaciPoPrimerku = zadatakRepository
                .zaPrimerke(primerci.stream().map(TestPrimerak::getId).toList()).stream()
                .collect(Collectors.groupingBy(TestPrimerakZadatak::getPrimerakId, LinkedHashMap::new, Collectors.toList()));
        List<TestIzbor> izbor = izborRepository.findByTestIdOrderByStavkaIdAscGrupaTestaAscMestoAsc(test.getId()).stream()
                .filter(i -> i.getStavkaId().equals(st.getId())).toList();

        Integer nivo = st.getIzabraniNivo();
        boolean sveMesta = cmd.mesto() == null;
        if (cmd.nivo() != null) {
            Nivo.proveri(cmd.nivo());
            if (!IzvlacenjeTesta.dozvoljeni(SablonTestaService.nivoiIzKolone(st.getNivoi())).contains(cmd.nivo())) {
                throw bad("Nivo nije dozvoljen za stavku.");
            }
            nivo = cmd.nivo();
            st.setIzabraniNivo(nivo);
            stavkaRepository.save(st);
        }
        final int n = nivo == null ? Nivo.PODRAZUMEVANI : nivo;
        LinkedHashMap<Long, List<Kandidat>> poTipu = IzvlacenjeTesta.zaNivo(opis.stavka(), n);
        if (poTipu.isEmpty()) throw conflict("Stavka „" + st.getNaziv() + "“ " + NEMA_KANDIDATA);
        if (cmd.tipId() != null && !poTipu.containsKey(cmd.tipId())) throw bad("Tip nije u izvorima stavke.");

        // jedinice i trenutne dodele po mestu
        Map<Object, List<TestPrimerak>> primerciJedinice = new LinkedHashMap<>();
        if (grupe) {
            for (TestGrupa g : grupeTesta) primerciJedinice.put(g, new ArrayList<>());
            primerci.stream().filter(p -> p.getStanje() != StanjePrimerka.PONISTEN)
                    .forEach(p -> primerciJedinice.computeIfAbsent(p.getGrupaTesta(), x -> new ArrayList<>()).add(p));
        } else {
            primerci.forEach(p -> primerciJedinice.put(p.getStudent().getId(), List.of(p)));
        }
        List<Object> ciljane = grupe && cmd.grupaTesta() != null ? List.of(cmd.grupaTesta())
                : new ArrayList<>(primerciJedinice.keySet());
        Map<Long, Set<Long>> istorija = istorija(test, primerci.stream().map(p -> p.getStudent().getId()).toList());
        Set<Long> objavljena = new HashSet<>(zadatakRepository.objavljenaResenja(test.getPredmet().getId(), test.getId()));
        Map<Long, TestGrupa> grupaStudenta = new HashMap<>();
        primerci.forEach(p -> grupaStudenta.put(p.getStudent().getId(), p.getGrupaTesta()));
        ToIntBiFunction<Object, Long> istorijskaKazna = kazna(grupe, grupaStudenta, istorija, objavljena);

        Random r = new Random(random.nextLong());
        List<Integer> mesta = sveMesta ? java.util.stream.IntStream.range(0, st.getBroj()).boxed().toList() : List.of(cmd.mesto());
        List<Long> redTipova = new ArrayList<>(poTipu.keySet());
        List<Long> noviTipovi = st.isIstiTip() && cmd.tipId() == null && (cmd.nivo() != null || ciljane.size() == primerciJedinice.size())
                ? IzvlacenjeTesta.tipoviPoMestima(redTipova, st.getBroj(), r) : null;

        Map<Object, Map<Integer, Long>> trenutno = trenutneVarijante(st, primerciJedinice, zadaciPoPrimerku, izbor, grupe);
        Map<Mesto<Object>, Long> nove = new LinkedHashMap<>();
        Map<Integer, Long> tipMesta = new HashMap<>();
        for (int m : mesta) {
            final int mesto = m;
            Long tip;
            if (cmd.tipId() != null) tip = cmd.tipId();
            else if (!st.isIstiTip()) tip = null;
            else if (noviTipovi != null) tip = noviTipovi.get(m);
            else tip = izbor.stream().filter(i -> i.getMesto() == mesto && i.getTipId() != null).map(TestIzbor::getTipId)
                    .filter(poTipu::containsKey).findFirst().orElse(redTipova.get(r.nextInt(redTipova.size())));
            tipMesta.put(m, tip);

            if (cmd.varijantaId() != null) {
                if (!grupe || cmd.grupaTesta() == null || cmd.mesto() == null) {
                    throw bad("Varijanta se bira samo za jednu grupu testa i jedno mesto.");
                }
                boolean kandidat = poTipu.values().stream().flatMap(List::stream).anyMatch(k -> k.varijantaId() == cmd.varijantaId());
                if (!kandidat) throw bad("Varijanta nije kandidat stavke.");
                nove.put(new Mesto<>(cmd.grupaTesta(), m), cmd.varijantaId());
                Kandidat izabran = poTipu.values().stream().flatMap(List::stream)
                        .filter(k -> k.varijantaId() == cmd.varijantaId()).findFirst().orElseThrow();
                tipMesta.put(m, izabran.tipId());
                continue;
            }
            Map<Long, Integer> brojac = new HashMap<>();
            for (Object j : primerciJedinice.keySet()) {
                if (ciljane.contains(j)) continue;
                Long v = trenutno.getOrDefault(j, Map.of()).get(m);
                if (v != null) brojac.merge(v, 1, Integer::sum);
            }
            var d = IzvlacenjeTesta.rasporedi(ciljane,
                    j -> tip != null ? poTipu.getOrDefault(tip, List.of())
                            : poTipu.get(redTipova.get(r.nextInt(redTipova.size()))),
                    brojac,
                    j -> zauzetoOsim(j, st.getId(), mesto, primerciJedinice, zadaciPoPrimerku, grupe, izbor),
                    (j, v) -> istorijskaKazna.applyAsInt(j, v)
                            + (Objects.equals(trenutno.getOrDefault(j, Map.of()).get(mesto), v) ? 1000 : 0),
                    r);
            d.izbor().forEach((j, k) -> nove.put(new Mesto<>(j, mesto), k.varijantaId()));
        }

        Map<Long, Varijanta> varijante = zakljucajIUcitaj(new HashSet<>(nove.values()));
        // plan izvlačenja
        for (TestIzbor i : izbor) {
            if (!mesta.contains(i.getMesto())) continue;
            if (grupe) {
                Long v = nove.get(new Mesto<>((Object) i.getGrupaTesta(), i.getMesto()));
                if (v != null) {
                    i.setVarijantaId(v);
                    i.setTipId(varijante.get(v).getGrupa().getId());
                }
            } else {
                i.setTipId(tipMesta.get(i.getMesto()));
            }
        }
        izborRepository.saveAll(izbor);
        // primerci
        for (var e : primerciJedinice.entrySet()) {
            for (TestPrimerak p : e.getValue()) {
                for (TestPrimerakZadatak z : zadaciPoPrimerku.getOrDefault(p.getId(), List.of())) {
                    if (!st.getId().equals(z.getStavkaId())) continue;
                    Long v = nove.get(new Mesto<>(e.getKey(), z.getMesto()));
                    if (v == null) continue;
                    postaviVarijantu(z, st, varijante.get(v));
                }
            }
        }
        zadatakRepository.saveAll(zadaciPoPrimerku.values().stream().flatMap(List::stream).toList());
        return online(test);
    }

    /** Student dodat naknadno (zakasnio, nije bio na spisku): u svakom stanju osim ZAVRSEN. */
    public OnlineTest dodajStudenta(Long testId, StudentIdCmd cmd) {
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        obradiIstek(test, smena);
        if (StanjeTesta.stanje(smena) == StanjeOnline.ZAVRSEN) throw conflict(TEST_ZAVRSEN);
        if (cmd == null || cmd.studentId() == null) throw bad("Student je obavezan.");
        Student s = studentRepository.findById(cmd.studentId())
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + cmd.studentId(), HttpStatus.NOT_FOUND));
        if (!Utility.smeNaNastavuGrupe(s, test.getGrupa())) {
            throw bad("Student " + s.getIndeks() + " ne pripada grupi " + test.getGrupa().getNaziv());
        }
        if (primerakRepository.findByTestIdAndStudentId(test.getId(), s.getId()).isPresent()) {
            throw conflict("Student je već na testu.");
        }
        List<TestPrimerak> primerci = primerakRepository.zaTestForUpdate(test.getId());
        List<TestStavka> stavke = stavkaRepository.findByTestIdOrderByRb(test.getId());
        List<TestIzbor> izbor = izborRepository.findByTestIdOrderByStavkaIdAscGrupaTestaAscMestoAsc(test.getId());
        Map<Long, List<TestPrimerakZadatak>> zadaciPoPrimerku = zadatakRepository
                .zaPrimerke(primerci.stream().map(TestPrimerak::getId).toList()).stream()
                .collect(Collectors.groupingBy(TestPrimerakZadatak::getPrimerakId));
        boolean grupe = test.getRezim() == RezimTesta.GRUPE;

        TestPrimerak p = new TestPrimerak();
        p.setTestId(test.getId());
        p.setStudent(s);
        p.setSmenaId(smena.getId());
        Map<Long, Varijanta> varijante;
        Map<String, Long> izabrano = new LinkedHashMap<>();   // "stavka:mesto" -> varijanta
        if (grupe) {
            List<TestGrupa> grupeTesta = grupeTesta(test);
            Map<TestGrupa, Integer> velicine = new HashMap<>();
            primerci.stream().filter(x -> x.getStanje() != StanjePrimerka.PONISTEN)
                    .forEach(x -> velicine.merge(x.getGrupaTesta(), 1, Integer::sum));
            TestGrupa g = IzvlacenjeTesta.najmanjaGrupa(grupeTesta, velicine);
            p.setGrupaTesta(g);
            izbor.stream().filter(i -> i.getGrupaTesta() == g)
                    .forEach(i -> izabrano.put(i.getStavkaId() + ":" + i.getMesto(), i.getVarijantaId()));
        } else {
            StabloPredavanja stablo = kandidatiTesta.stablo(test.getPredmet().getId());
            Set<Long> istorija = istorija(test, List.of(s.getId())).getOrDefault(s.getId(), Set.of());
            Set<Long> objavljena = new HashSet<>(zadatakRepository.objavljenaResenja(test.getPredmet().getId(), test.getId()));
            Set<Long> zauzeto = new HashSet<>();
            Random r = new Random(random.nextLong());
            for (TestStavka st : stavke) {
                KandidatiTesta.OpisStavke opis = opis(stablo, st, test);
                int n = st.getIzabraniNivo() == null ? Nivo.PODRAZUMEVANI : st.getIzabraniNivo();
                LinkedHashMap<Long, List<Kandidat>> poTipu = IzvlacenjeTesta.zaNivo(opis.stavka(), n);
                if (poTipu.isEmpty()) throw conflict("Stavka „" + st.getNaziv() + "“ " + NEMA_KANDIDATA);
                List<Long> redTipova = new ArrayList<>(poTipu.keySet());
                for (int m = 0; m < st.getBroj(); m++) {
                    final int mesto = m;
                    Long tip = izbor.stream().filter(i -> i.getStavkaId().equals(st.getId()) && i.getMesto() == mesto)
                            .map(TestIzbor::getTipId).filter(Objects::nonNull).filter(poTipu::containsKey).findFirst().orElse(null);
                    List<Kandidat> skup = tip != null ? poTipu.get(tip) : poTipu.get(redTipova.get(r.nextInt(redTipova.size())));
                    Map<Long, Integer> brojac = new HashMap<>();
                    zadaciPoPrimerku.values().stream().flatMap(List::stream)
                            .filter(z -> st.getId().equals(z.getStavkaId()) && z.getMesto() == mesto)
                            .forEach(z -> brojac.merge(z.getVarijantaId(), 1, Integer::sum));
                    var d = IzvlacenjeTesta.rasporedi(List.of((Object) s.getId()), j -> skup, brojac, j -> zauzeto,
                            (j, v) -> (istorija.contains(v) ? 1 : 0) + (objavljena.contains(v) ? 1 : 0), r);
                    Kandidat k = d.izbor().get(s.getId());
                    zauzeto.add(k.varijantaId());
                    izabrano.put(st.getId() + ":" + m, k.varijantaId());
                }
            }
        }
        if (izabrano.values().stream().anyMatch(Objects::isNull)) throw conflict(BANKA_SE_MENJA);
        p = primerakRepository.save(p);
        // snimak se prepisuje iz postojećeg primerka iste grupe kad ga ima (isti zadaci kao ostali), inače iz banke
        Map<String, TestPrimerakZadatak> uzor = new HashMap<>();
        if (grupe) {
            TestGrupa g = p.getGrupaTesta();
            primerci.stream().filter(x -> x.getGrupaTesta() == g).findFirst().ifPresent(x ->
                    zadaciPoPrimerku.getOrDefault(x.getId(), List.of()).forEach(z -> uzor.put(z.getStavkaId() + ":" + z.getMesto(), z)));
        }
        Set<Long> izBanke = new HashSet<>();
        izabrano.forEach((k, v) -> {
            TestPrimerakZadatak u = uzor.get(k);
            if (u == null || !u.getVarijantaId().equals(v)) izBanke.add(v);
        });
        varijante = izBanke.isEmpty() ? Map.of() : zakljucajIUcitaj(izBanke);
        int rb = 1;
        for (TestStavka st : stavke) {
            for (int m = 0; m < st.getBroj(); m++) {
                String kljuc = st.getId() + ":" + m;
                Long v = izabrano.get(kljuc);
                TestPrimerakZadatak u = uzor.get(kljuc);
                TestPrimerakZadatak z;
                if (u != null && u.getVarijantaId().equals(v)) {
                    z = new TestPrimerakZadatak();
                    z.setPrimerakId(p.getId());
                    z.setRb(rb++);
                    z.setStavkaId(st.getId());
                    z.setMesto(m);
                    z.setVarijantaId(v);
                    z.setSnimak(u.getSnimak());
                    z.setMaxPoena(u.getMaxPoena());
                } else {
                    z = zadatak(p, rb++, st, m, varijante.get(v));
                }
                zadatakRepository.save(z);
            }
        }
        log.info("Student dodat na online test: test={}, student={}", test.getId(), s.getId());
        return online(test);
    }

    /** Uklanja studenta sa testa, samo u pripremi. */
    public OnlineTest ukloniStudenta(Long testId, Long studentId) {
        Test test = zakljucaj(testId);
        if (StanjeTesta.stanje(smena(test)) != StanjeOnline.PRIPREMA) throw conflict(SAMO_PRIPREMA);
        TestPrimerak p = primerakRepository.findByTestIdAndStudentId(test.getId(), studentId)
                .orElseThrow(() -> new SystemException("Student nije na testu.", HttpStatus.NOT_FOUND));
        zadatakRepository.obrisiZaPrimerak(p.getId());
        primerakRepository.delete(p);
        primerakRepository.flush();
        return online(test);
    }

    // ================================================================ tok

    public OnlineTest otvoriUlaz(Long testId, OtvoriUlazCmd cmd) {
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        if (StanjeTesta.stanje(smena) != StanjeOnline.PRIPREMA) throw conflict("Ulaz je već otvoren.");
        if (primerakRepository.countByTestId(test.getId()) == 0) throw conflict("Test nema studenata.");
        smena.setUlazOtvoren(LocalDateTime.now(clock));
        smena.setUlazniKod(cmd != null && Boolean.TRUE.equals(cmd.ulazniKod()) ? "%04d".formatted(KOD.nextInt(10_000)) : null);
        smenaRepository.save(smena);
        log.info("Online test: ulaz otvoren, test={}, kod={}", test.getId(), smena.getUlazniKod() != null);
        return online(test);
    }

    public OnlineTest pokreni(Long testId) {
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        StanjeOnline stanje = StanjeTesta.stanje(smena);
        if (stanje == StanjeOnline.PRIPREMA) throw conflict("Prvo otvori ulaz.");
        if (stanje != StanjeOnline.ULAZ) throw conflict("Test je već pokrenut.");
        LocalDateTime sada = LocalDateTime.now(clock);
        smena.setPokrenuta(sada);
        smena.setRok(sada.plusMinutes(test.getTrajanjeMin()));
        smenaRepository.save(smena);
        log.info("Online test pokrenut: test={}", test.getId());
        return online(test);
    }

    public OnlineTest zavrsi(Long testId) {
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        StanjeOnline stanje = StanjeTesta.stanje(smena);
        if (stanje == StanjeOnline.PRIPREMA) throw conflict("Test nije počeo.");
        if (stanje == StanjeOnline.ZAVRSEN) throw conflict(TEST_ZAVRSEN);
        zatvori(test, smena, LocalDateTime.now(clock));
        return online(test);
    }

    public KonzolaTesta produzi(Long testId, ProduziCmd cmd) {
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        obradiIstek(test, smena);
        StanjeOnline stanje = StanjeTesta.stanje(smena);
        if (stanje == StanjeOnline.PRIPREMA || stanje == StanjeOnline.ZAVRSEN) throw conflict("Test nije u toku.");
        if (cmd == null || cmd.minuti() == null || cmd.minuti() < 1 || cmd.minuti() > 120) {
            throw bad("Produženje je od 1 do 120 minuta.");
        }
        List<TestPrimerak> primerci = primerakRepository.zaTestForUpdate(test.getId());
        List<TestPrimerak> ciljani;
        if (cmd.primerakId() != null) {
            TestPrimerak p = primerci.stream().filter(x -> x.getId().equals(cmd.primerakId())).findFirst()
                    .orElseThrow(() -> new SystemException(NEMA_PRIMERKA, HttpStatus.NOT_FOUND));
            if (!StanjeTesta.otvoren(p)) throw conflict("Test je predat.");
            ciljani = List.of(p);
        } else {
            ciljani = primerci.stream().filter(StanjeTesta::otvoren).toList();
        }
        LocalDateTime sada = LocalDateTime.now(clock);
        for (TestPrimerak p : ciljani) {
            p.setProduzenjeMin(p.getProduzenjeMin() + cmd.minuti());
            dogadjaj(p, "PRODUZENJE", "+" + cmd.minuti() + " min", sada);
        }
        primerakRepository.saveAll(ciljani);
        return konzola(test, smena);
    }

    public KonzolaTesta vrati(Long testId, Long primerakId) {
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        obradiIstek(test, smena);
        if (smena.getZatvorena() != null) throw conflict("Smena je zatvorena.");
        TestPrimerak p = primerak(test, primerakId);
        if (p.getStanje() != StanjePrimerka.PREDAT) throw conflict("Primerak nije predat.");
        p.setStanje(StanjePrimerka.U_TOKU);
        p.setPredato(null);
        p.setNacinPredaje(null);
        p.setPoeni(null);
        primerakRepository.save(p);
        ocenjivanje.obrisiPolaganje(test, p);
        dogadjaj(p, "VRACEN", null, LocalDateTime.now(clock));
        return konzola(test, smena);
    }

    public KonzolaTesta ponisti(Long testId, Long primerakId, NapomenaCmd cmd) {
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        TestPrimerak p = primerak(test, primerakId);
        if (p.getStanje() == StanjePrimerka.PONISTEN) throw conflict("Primerak je već poništen.");
        String napomena = cmd == null || cmd.napomena() == null ? null : cmd.napomena().strip();
        if (napomena != null && napomena.length() > 500) throw bad("Napomena je predugačka.");
        p.setStanje(StanjePrimerka.PONISTEN);
        p.setNapomena(napomena == null || napomena.isEmpty() ? null : napomena);
        p.setPoeni(null);
        if (p.getPredato() == null) p.setPredato(LocalDateTime.now(clock));
        primerakRepository.save(p);
        ocenjivanje.obrisiPolaganje(test, p);
        dogadjaj(p, "PONISTEN", null, LocalDateTime.now(clock));
        return konzola(test, smena);
    }

    public KonzolaTesta prepisivao(Long testId, Long primerakId, PrepisivaoCmd cmd) {
        if (cmd == null || cmd.prepisivao() == null) throw bad("Polje prepisivao je obavezno.");
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        TestPrimerak p = primerak(test, primerakId);
        p.setPrepisivao(cmd.prepisivao());
        primerakRepository.save(p);
        if (p.getStanje() == StanjePrimerka.PREDAT) ocenjivanje.upisiPolaganje(test, p);
        return konzola(test, smena);
    }

    public KonzolaTesta konzola(Long testId) {
        Test test = zakljucaj(testId);
        TestSmena smena = smena(test);
        obradiIstek(test, smena);
        return konzola(test, smena);
    }

    public OnlineTest online(Long testId) {
        Test test = zakljucaj(testId);
        obradiIstek(test, smena(test));
        return online(test);
    }

    // ================================================================ istek

    /** Zakazani posao: zatvara istekle smene i zaključava istekle primerke (svaki test u svojoj transakciji). */
    public void obradiIstekTesta(Long testId) {
        Test test = testRepository.findByIdForUpdate(testId).orElse(null);
        if (test == null || !test.isOnline()) return;
        smenaRepository.findFirstByTestIdOrderByRb(testId).ifPresent(s -> obradiIstek(test, s));
    }

    /**
     * Lenji istek (pozivalac drži ključ testa): primerci kojima je rok uz toleranciju prošao se zaključavaju ({@code ISTEK});
     * kad je rok prošao svima otvorenim, smena se zatvara.
     */
    void obradiIstek(Test test, TestSmena smena) {
        if (smena.getPokrenuta() == null || smena.getZatvorena() != null) return;
        LocalDateTime sada = LocalDateTime.now(clock);
        List<TestPrimerak> primerci = primerakRepository.zaTestForUpdate(test.getId());
        Map<Long, BodovanjeZadatka.Ispravke> isp = null;
        boolean svimaIstekao = true;
        for (TestPrimerak p : primerci) {
            if (!StanjeTesta.otvoren(p)) continue;
            if (!StanjeTesta.istekao(smena, p, sada)) {
                svimaIstekao = false;
                continue;
            }
            if (p.getStanje() == StanjePrimerka.U_TOKU) {
                if (isp == null) isp = ocenjivanje.ispravke(test.getId());
                predaj(test, p, NacinPredaje.ISTEK, sada, isp);
            }
        }
        if (svimaIstekao && StanjeTesta.istekao(smena, null, sada)) {
            zatvori(test, smena, sada);
        }
    }

    /** Zatvara smenu: otvoreni primerci koji rade se predaju (ISTEK kad im je rok prošao, inače NASTAVNIK). */
    private void zatvori(Test test, TestSmena smena, LocalDateTime sada) {
        List<TestPrimerak> primerci = primerakRepository.zaTestForUpdate(test.getId());
        Map<Long, BodovanjeZadatka.Ispravke> isp = ocenjivanje.ispravke(test.getId());
        if (smena.getPokrenuta() != null) {
            for (TestPrimerak p : primerci) {
                if (p.getStanje() == StanjePrimerka.U_TOKU) {
                    predaj(test, p, StanjeTesta.istekao(smena, p, sada) ? NacinPredaje.ISTEK : NacinPredaje.NASTAVNIK, sada, isp);
                }
            }
        }
        smena.setZatvorena(sada);
        smenaRepository.save(smena);
        List<Long> studenti = primerci.stream().map(p -> p.getStudent().getId()).toList();
        if (!studenti.isEmpty()) {
            uredjajRepository.skratiVazenje(studenti, NacinPrijave.RACUNAR, sada.plusMinutes(5));
        }
        log.info("Online test: smena zatvorena, test={}", test.getId());
    }

    /** Predaja primerka (pozivalac drži ključ primerka) i ocena u polaganje. */
    void predaj(Test test, TestPrimerak p, NacinPredaje nacin, LocalDateTime sada, Map<Long, BodovanjeZadatka.Ispravke> isp) {
        p.setStanje(StanjePrimerka.PREDAT);
        p.setPredato(sada);
        p.setNacinPredaje(nacin);
        primerakRepository.save(p);
        dogadjaj(p, nacin == NacinPredaje.ISTEK ? "ISTEK" : "PREDAO", nacin == NacinPredaje.NASTAVNIK ? "nastavnik" : null, sada);
        ocenjivanje.oceni(test, p, isp);
    }

    // ================================================================ prikaz

    OnlineTest online(Test test) {
        TestSmena smena = smena(test);
        List<TestStavka> stavke = stavkaRepository.findByTestIdOrderByRb(test.getId());
        List<TestPrimerak> primerci = primerakRepository.zaTest(test.getId());
        List<TestPrimerakZadatak> zadaci = zadatakRepository.zaPrimerke(primerci.stream().map(TestPrimerak::getId).toList());
        List<TestIzbor> izbor = izborRepository.findByTestIdOrderByStavkaIdAscGrupaTestaAscMestoAsc(test.getId());
        StabloPredavanja stablo = kandidatiTesta.stablo(test.getPredmet().getId());
        Map<Long, TestStavka> stavkaPoId = stavke.stream().collect(Collectors.toMap(TestStavka::getId, s -> s));
        Map<Long, Integer> rbStavke = stavke.stream().collect(Collectors.toMap(TestStavka::getId, TestStavka::getRb));
        List<String> upozorenja = new ArrayList<>();

        List<StavkaTesta> stavkeDto = new ArrayList<>();
        for (TestStavka st : stavke) {
            KandidatiTesta.OpisStavke o = opis(stablo, st, test);
            stavkeDto.add(new StavkaTesta(st.getId(), st.getRb(), st.getNaziv(), st.getPoeni(), st.getBroj(),
                    List.copyOf(SablonTestaService.nivoiIzKolone(st.getNivoi())), st.getIzabraniNivo(), st.isIstiTip(),
                    st.isRucno(), o.izvori(), o.tipovi()));
        }

        Map<Long, TestPrimerak> primerakPoId = primerci.stream().collect(Collectors.toMap(TestPrimerak::getId, p -> p));
        List<GrupaIzbora> izborDto = new ArrayList<>();
        Comparator<ZadatakIzbora> red = Comparator.<ZadatakIzbora, Integer>comparing(z -> rbStavke.getOrDefault(z.stavkaId(), 0))
                .thenComparingInt(ZadatakIzbora::mesto).thenComparingLong(ZadatakIzbora::varijantaId);
        List<TestPrimerak> aktivni = primerci.stream().filter(p -> p.getStanje() != StanjePrimerka.PONISTEN).toList();
        if (test.getRezim() == RezimTesta.GRUPE) {
            Map<String, TestPrimerakZadatak> uzor = new HashMap<>();
            Map<String, Integer> brojStudenata = new HashMap<>();
            for (TestPrimerakZadatak z : zadaci) {
                TestPrimerak p = primerakPoId.get(z.getPrimerakId());
                if (p == null || p.getStanje() == StanjePrimerka.PONISTEN) continue;
                String k = p.getGrupaTesta() + ":" + z.getStavkaId() + ":" + z.getMesto() + ":" + z.getVarijantaId();
                uzor.putIfAbsent(k, z);
                brojStudenata.merge(k, 1, Integer::sum);
            }
            Set<Long> bezUzora = new HashSet<>();
            for (TestIzbor i : izbor) {
                String k = i.getGrupaTesta() + ":" + i.getStavkaId() + ":" + i.getMesto() + ":" + i.getVarijantaId();
                if (!uzor.containsKey(k) && i.getVarijantaId() != null) bezUzora.add(i.getVarijantaId());
            }
            Map<Long, Varijanta> izBanke = bezUzora.isEmpty() ? Map.of()
                    : varijantaRepository.findAllById(bezUzora).stream().collect(Collectors.toMap(Varijanta::getId, v -> v));
            for (TestGrupa g : grupeTesta(test)) {
                List<ZadatakIzbora> z = new ArrayList<>();
                for (TestIzbor i : izbor) {
                    if (i.getGrupaTesta() != g || i.getVarijantaId() == null) continue;
                    TestStavka st = stavkaPoId.get(i.getStavkaId());
                    if (st == null) continue;
                    String k = g + ":" + i.getStavkaId() + ":" + i.getMesto() + ":" + i.getVarijantaId();
                    SnimakZadatkaTesta s = uzor.containsKey(k) ? TestJson.snimak(uzor.get(k).getSnimak())
                            : izBanke.containsKey(i.getVarijantaId())
                            ? KandidatiTesta.snimak(izBanke.get(i.getVarijantaId()), st.getNaziv(), st.getPoeni(), st.isRucno()) : null;
                    if (s == null) continue;
                    z.add(zadatakIzbora(st.getId(), i.getMesto(), s, brojStudenata.getOrDefault(k, 0)));
                }
                z.sort(red);
                int studenata = (int) aktivni.stream().filter(p -> p.getGrupaTesta() == g).count();
                izborDto.add(new GrupaIzbora(g, studenata, List.copyOf(z)));
            }
            // grupe dele varijantu
            for (TestStavka st : stavke) {
                for (int m = 0; m < st.getBroj(); m++) {
                    final int mesto = m;
                    long razlicitih = izbor.stream().filter(i -> i.getStavkaId().equals(st.getId()) && i.getMesto() == mesto)
                            .map(TestIzbor::getVarijantaId).distinct().count();
                    long grupa = izbor.stream().filter(i -> i.getStavkaId().equals(st.getId()) && i.getMesto() == mesto).count();
                    if (razlicitih < grupa) {
                        upozorenja.add("Stavka „" + st.getNaziv() + "“: grupe dele varijantu (" + razlicitih + " različitih za "
                                + KandidatiTesta.grupa((int) grupa) + ").");
                        break;
                    }
                }
            }
        } else {
            Map<String, TestPrimerakZadatak> uzor = new LinkedHashMap<>();
            Map<String, Integer> broj = new HashMap<>();
            for (TestPrimerakZadatak z : zadaci) {
                TestPrimerak p = primerakPoId.get(z.getPrimerakId());
                if (p == null || p.getStanje() == StanjePrimerka.PONISTEN) continue;
                String k = z.getStavkaId() + ":" + z.getMesto() + ":" + z.getVarijantaId();
                uzor.putIfAbsent(k, z);
                broj.merge(k, 1, Integer::sum);
            }
            List<ZadatakIzbora> z = new ArrayList<>();
            uzor.forEach((k, zad) -> z.add(zadatakIzbora(zad.getStavkaId(), zad.getMesto(), TestJson.snimak(zad.getSnimak()),
                    broj.get(k))));
            z.sort(red);
            izborDto.add(new GrupaIzbora(null, aktivni.size(), List.copyOf(z)));
        }

        // ponavljanja u primerku i sa ranijih testova (samo dok se priprema)
        if (StanjeTesta.stanje(smena) == StanjeOnline.PRIPREMA && !primerci.isEmpty()) {
            Map<Long, Set<Long>> istorija = istorija(test, primerci.stream().map(p -> p.getStudent().getId()).toList());
            int ponavljanja = 0;
            boolean uPrimerku = false;
            Map<Long, List<TestPrimerakZadatak>> poPrimerku = zadaci.stream()
                    .collect(Collectors.groupingBy(TestPrimerakZadatak::getPrimerakId));
            for (TestPrimerak p : aktivni) {
                List<TestPrimerakZadatak> l = poPrimerku.getOrDefault(p.getId(), List.of());
                Set<Long> vidjene = istorija.getOrDefault(p.getStudent().getId(), Set.of());
                ponavljanja += (int) l.stream().filter(z -> vidjene.contains(z.getVarijantaId())).count();
                if (l.stream().map(TestPrimerakZadatak::getVarijantaId).distinct().count() < l.size()) uPrimerku = true;
            }
            if (ponavljanja > 0) {
                upozorenja.add(ponavljanja + " " + KandidatiTesta.oblik(ponavljanja, "zadatak se ponavlja", "zadatka se ponavljaju",
                        "zadataka se ponavlja") + " sa ranijih testova.");
            }
            if (uPrimerku) upozorenja.add("U nekom primerku se ista varijanta javlja dvaput (premalo kandidata).");
        }

        return new OnlineTest(test.getId(), test.getTipTesta().getNaziv(), test.getPredmet().getId(), test.getPredmet().getNaziv(),
                test.getGrupa().getId(), test.getGrupa().getNaziv(), test.getDatum(), StanjeTesta.stanje(smena), test.getRezim(),
                test.getRezim() == RezimTesta.GRUPE ? grupeTesta(test).size() : 1, test.getTrajanjeMin() == null ? 0 : test.getTrajanjeMin(),
                test.getMaxPoena() == null ? 0 : test.getMaxPoena(), test.getPragProlaza(), Boolean.TRUE.equals(test.getPregledan()),
                test.getObjavljeno(), test.isObjavaTacni(), test.getSablonId(), smenaDto(smena),
                clock.millis(), List.copyOf(stavkeDto), List.copyOf(izborDto),
                aktivni.size(), List.copyOf(upozorenja));
    }

    KonzolaTesta konzola(Test test, TestSmena smena) {
        List<TestPrimerak> primerci = primerakRepository.zaTest(test.getId());
        List<Long> ids = primerci.stream().map(TestPrimerak::getId).toList();
        Map<Long, List<TestPrimerakZadatak>> zadaci = zadatakRepository.zaPrimerke(ids).stream()
                .collect(Collectors.groupingBy(TestPrimerakZadatak::getPrimerakId));
        Map<Long, List<TestDogadjaj>> dogadjaji = ids.isEmpty() ? Map.of() : dogadjajRepository.zaPrimerke(ids).stream()
                .collect(Collectors.groupingBy(TestDogadjaj::getPrimerakId));
        Set<Long> uredjaji = primerci.stream().map(TestPrimerak::getAktivniUredjajId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> racunari = uredjaji.isEmpty() ? Set.of() : uredjajRepository.findAllById(uredjaji).stream()
                .filter(u -> u.getNacin() == NacinPrijave.RACUNAR).map(StudentskiUredjaj::getId).collect(Collectors.toSet());
        Set<Long> drugaMreza = drugaMreza(primerci);
        List<StudentKonzole> studenti = new ArrayList<>();
        int usli = 0;
        int predalo = 0;
        for (TestPrimerak p : primerci) {
            Student s = p.getStudent();
            int popunjeno = 0;
            int ukupno = 0;
            for (TestPrimerakZadatak z : zadaci.getOrDefault(p.getId(), List.of())) {
                SnimakZadatkaTesta sn = TestJson.snimak(z.getSnimak());
                Map<String, Object> uneto = TestJson.uneto(z.getOdgovori());
                for (var polje : sn.poljaZaUnos()) {
                    ukupno++;
                    if (uneto != null && !BodovanjeZadatka.prazno(uneto.get(polje.kljuc()))) popunjeno++;
                }
            }
            List<TestDogadjaj> d = dogadjaji.getOrDefault(p.getId(), List.of());
            int napustanja = (int) d.stream().filter(x -> "NAPUSTIO".equals(x.getVrsta())).count();
            long ms = d.stream().filter(x -> "VRATIO".equals(x.getVrsta())).mapToLong(x -> parseMs(x.getDetalj())).sum();
            int promena = (int) d.stream().filter(x -> "PREUZEO".equals(x.getVrsta())).count();
            if (p.getUsao() != null) usli++;
            if (p.getStanje() == StanjePrimerka.PREDAT) predalo++;
            studenti.add(new StudentKonzole(p.getId(), s.getId(), s.getIme(), s.getPrezime(), s.getIndeks(), p.getGrupaTesta(),
                    StanjeTesta.konzola(smena, p), popunjeno, ukupno, StanjeTesta.ms(StanjeTesta.rok(smena, p), clock),
                    p.getProduzenjeMin(), p.getPredato(), p.getAktivniUredjajId() != null && racunari.contains(p.getAktivniUredjajId()),
                    promena, napustanja, (int) (ms / 1000), drugaMreza.contains(p.getId()), p.isPrepisivao(), p.getPoeni()));
        }
        return new KonzolaTesta(test.getId(), StanjeTesta.stanje(smena), smenaDto(smena), clock.millis(),
                test.getMaxPoena() == null ? 0 : test.getMaxPoena(), usli, predalo, primerci.size(), List.copyOf(studenti));
    }

    /** Primerci čija se mreža razlikuje od većine u smeni (tek kad ima bar pet učesnika sa adresom). */
    static Set<Long> drugaMreza(List<TestPrimerak> primerci) {
        Map<Long, String> mreza = new HashMap<>();
        for (TestPrimerak p : primerci) {
            if (p.getIp() != null) mreza.put(p.getId(), mreza(p.getIp()));
        }
        if (mreza.size() < 5) return Set.of();
        Map<String, Long> broj = mreza.values().stream().collect(Collectors.groupingBy(m -> m, Collectors.counting()));
        String vecina = broj.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        return mreza.entrySet().stream().filter(e -> !e.getValue().equals(vecina)).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /** Mreža adrese: IPv4 /24, IPv6 prve četiri grupe. */
    static String mreza(String ip) {
        if (ip.contains(":")) {
            String[] g = ip.split(":");
            return String.join(":", Arrays.copyOf(g, Math.min(4, g.length)));
        }
        int i = ip.lastIndexOf('.');
        return i < 0 ? ip : ip.substring(0, i);
    }

    private static long parseMs(String s) {
        try {
            return s == null ? 0 : Math.max(0, Long.parseLong(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    SmenaTesta smenaDto(TestSmena s) {
        return new SmenaTesta(s.getId(), s.getUlazniKod(), s.getUlazOtvoren(), s.getPokrenuta(), StanjeTesta.ms(s.getRok(), clock),
                s.getZatvorena());
    }

    private static ZadatakIzbora zadatakIzbora(Long stavkaId, int mesto, SnimakZadatkaTesta s, int studenata) {
        return new ZadatakIzbora(stavkaId, mesto, s.varijantaId(), s.tipId(), s.tip(), s.nivo(), s.svgId(), s.resenjeSvgId(),
                s.polja(), s.odgovori(), s.maxPoena(), studenata);
    }

    // ================================================================ pomoćno

    Test zakljucaj(Long testId) {
        Test test = testRepository.findByIdForUpdate(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, HttpStatus.NOT_FOUND));
        if (!test.isOnline()) throw conflict(NIJE_ONLINE);
        return test;
    }

    TestSmena smena(Test test) {
        return smenaRepository.findFirstByTestIdOrderByRb(test.getId())
                .orElseThrow(() -> new SystemException("Test nema smenu.", HttpStatus.CONFLICT));
    }

    private TestPrimerak primerak(Test test, Long primerakId) {
        TestPrimerak p = primerakRepository.findByIdForUpdate(primerakId)
                .orElseThrow(() -> new SystemException(NEMA_PRIMERKA, HttpStatus.NOT_FOUND));
        if (!p.getTestId().equals(test.getId())) throw new SystemException(NEMA_PRIMERKA, HttpStatus.NOT_FOUND);
        return p;
    }

    static List<TestGrupa> grupeTesta(Test test) {
        if (test.getGrupe() == null) return List.of();
        return Arrays.stream(TestGrupa.values()).filter(test.getGrupe()::contains).toList();
    }

    private KandidatiTesta.OpisStavke opis(StabloPredavanja stablo, TestStavka st, Test test) {
        return kandidatiTesta.opis(stablo, st.getId(), st.getNaziv(), st.getBroj(), st.isIstiTip(), st.isRucno(),
                SablonTestaService.nivoiIzKolone(st.getNivoi()), TestJson.ids(st.getIzvori()), test.getRezim(),
                grupeTesta(test).size());
    }

    private Map<Long, Set<Long>> istorija(Test test, Collection<Long> studenti) {
        Map<Long, Set<Long>> rezultat = new HashMap<>();
        if (studenti.isEmpty()) return rezultat;
        for (Object[] red : zadatakRepository.ranijeVarijante(test.getPredmet().getId(), test.getId(), studenti)) {
            rezultat.computeIfAbsent(((Number) red[0]).longValue(), x -> new HashSet<>()).add(((Number) red[1]).longValue());
        }
        return rezultat;
    }

    /** Kazna varijante za jedinicu: koliko studenata jedinice ju je imalo ranije, plus 1 kad su njena rešenja objavljena. */
    private static ToIntBiFunction<Object, Long> kazna(boolean grupe, Map<Long, TestGrupa> grupaStudenta,
                                                       Map<Long, Set<Long>> istorija, Set<Long> objavljena) {
        if (grupe) {
            return (j, v) -> {
                int k = objavljena.contains(v) ? 1 : 0;
                for (var e : grupaStudenta.entrySet()) {
                    if (e.getValue() == j && istorija.getOrDefault(e.getKey(), Set.of()).contains(v)) k++;
                }
                return k;
            };
        }
        return (j, v) -> (istorija.getOrDefault((Long) j, Set.of()).contains(v) ? 1 : 0) + (objavljena.contains(v) ? 1 : 0);
    }

    private static Map<Object, Map<Integer, Long>> trenutneVarijante(TestStavka st, Map<Object, List<TestPrimerak>> jedinice,
                                                                     Map<Long, List<TestPrimerakZadatak>> zadaci,
                                                                     List<TestIzbor> izbor, boolean grupe) {
        Map<Object, Map<Integer, Long>> r = new HashMap<>();
        if (grupe) {
            for (TestIzbor i : izbor) {
                if (i.getVarijantaId() != null) r.computeIfAbsent(i.getGrupaTesta(), x -> new HashMap<>()).put(i.getMesto(), i.getVarijantaId());
            }
            return r;
        }
        jedinice.forEach((j, l) -> {
            for (TestPrimerak p : l) {
                for (TestPrimerakZadatak z : zadaci.getOrDefault(p.getId(), List.of())) {
                    if (st.getId().equals(z.getStavkaId())) r.computeIfAbsent(j, x -> new HashMap<>()).put(z.getMesto(), z.getVarijantaId());
                }
            }
        });
        return r;
    }

    /** Varijante u primerku jedinice osim zadatka (stavka, mesto) koji se izvlači ponovo. */
    private static Set<Long> zauzetoOsim(Object j, Long stavkaId, int mesto, Map<Object, List<TestPrimerak>> jedinice,
                                         Map<Long, List<TestPrimerakZadatak>> zadaci, boolean grupe, List<TestIzbor> izborStavke) {
        Set<Long> z = new HashSet<>();
        List<TestPrimerak> l = jedinice.getOrDefault(j, List.of());
        if (!l.isEmpty()) {
            for (TestPrimerakZadatak t : zadaci.getOrDefault(l.get(0).getId(), List.of())) {
                if (!(stavkaId.equals(t.getStavkaId()) && t.getMesto() == mesto)) z.add(t.getVarijantaId());
            }
        } else if (grupe) {
            izborStavke.stream().filter(i -> i.getGrupaTesta() == j && i.getMesto() != mesto && i.getVarijantaId() != null)
                    .forEach(i -> z.add(i.getVarijantaId()));
        }
        return z;
    }

    /** Zaključava izabrane varijante (FOR UPDATE) i učitava ih; varijanta koja je u međuvremenu izašla iz kandidata je 409. */
    private Map<Long, Varijanta> zakljucajIUcitaj(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Long> zakljucane = varijantaRepository.zakljucajKandidate(ids);
        if (zakljucane.size() != ids.size()) throw conflict(BANKA_SE_MENJA);
        return varijantaRepository.findAllById(ids).stream().collect(Collectors.toMap(Varijanta::getId, v -> v));
    }

    private static TestIzbor izbor(Test test, TestSmena smena, TestGrupa g, TestStavka st, int m, Long tip, Long varijanta) {
        TestIzbor i = new TestIzbor();
        i.setTestId(test.getId());
        i.setSmenaId(smena.getId());
        i.setGrupaTesta(g);
        i.setStavkaId(st.getId());
        i.setMesto(m);
        i.setTipId(tip);
        i.setVarijantaId(varijanta);
        return i;
    }

    private static TestPrimerakZadatak zadatak(TestPrimerak p, int rb, TestStavka st, int m, Varijanta v) {
        TestPrimerakZadatak z = new TestPrimerakZadatak();
        z.setPrimerakId(p.getId());
        z.setRb(rb);
        z.setStavkaId(st.getId());
        z.setMesto(m);
        postaviVarijantu(z, st, v);
        return z;
    }

    private static void postaviVarijantu(TestPrimerakZadatak z, TestStavka st, Varijanta v) {
        z.setVarijantaId(v.getId());
        z.setMaxPoena(st.getPoeni());
        z.setSnimak(TestJson.uJson(KandidatiTesta.snimak(v, st.getNaziv(), st.getPoeni(), st.isRucno())));
    }

    void dogadjaj(TestPrimerak p, String vrsta, String detalj, LocalDateTime vreme) {
        TestDogadjaj d = new TestDogadjaj();
        d.setPrimerakId(p.getId());
        d.setVrsta(vrsta);
        d.setDetalj(detalj);
        d.setVreme(vreme);
        dogadjajRepository.save(d);
    }

    static SystemException bad(String poruka) {
        return new SystemException(poruka, HttpStatus.BAD_REQUEST);
    }

    static SystemException conflict(String poruka) {
        return new SystemException(poruka, HttpStatus.CONFLICT);
    }
}
