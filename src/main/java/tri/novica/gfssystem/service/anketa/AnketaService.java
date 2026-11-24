package tri.novica.gfssystem.service.anketa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.anketa.*;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.anketa.Anketa;
import tri.novica.gfssystem.entity.anketa.AnketaGlas;
import tri.novica.gfssystem.entity.anketa.AnketaSablon;
import tri.novica.gfssystem.entity.anketa.IzvorAnkete;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;
import tri.novica.gfssystem.entity.push.VrstaStudentskogObavestenja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.anketa.AnketaGlasRepository;
import tri.novica.gfssystem.repository.anketa.AnketaRepository;
import tri.novica.gfssystem.repository.anketa.AnketaSablonRepository;
import tri.novica.gfssystem.service.anketa.AnketaPravila.Stanje;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis.Prekidac;
import tri.novica.gfssystem.service.push.PushPoruka;
import tri.novica.gfssystem.service.push.PushPosiljalac;
import tri.novica.gfssystem.service.push.PushTekstovi;
import tri.novica.gfssystem.service.push.StudentskaObavestenja;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Brza anketa na času (spec brze ankete 3, 4, 5 i 7): pokretanje, zatvaranje, prikaz rezultata, brisanje, pregled za
 * nastavnika i MCP (samo zbirovi), skup anketa za studenta i glas. Zaseban model, ne runda izvođenja: Uživo se ne dira.
 * <p>
 * <b>Anonimnost:</b> nijedan nastavnički ni MCP odgovor ({@link AnketaDetails}, {@link AnketaInfo}) ne nosi id, ime ni glas
 * pojedinca; glasovi se čitaju samo kao {@code [anketaId, opcije]}. Student se uvek uzima iz sesije (REST), nikad iz zahteva.
 * <p>
 * <b>Brave:</b> pokretanje ankete na času zaključa red predavanja (kao čekiranje i {@code zavrsiPredavanje}), pa tek onda
 * zatvori prethodnu anketu tog predavanja; zatvaranje i prikaz rezultata zaključaju samo red ankete; glas drži deljenu bravu
 * na redu ankete ({@code FOR SHARE}), pa zatvaranje sačeka glasove u toku. Nijedna putanja ne uzima red predavanja posle reda
 * ankete. {@code READ_COMMITTED}, kao čekiranje.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class AnketaService {

    public static final int NAJVISE_OTVORENIH_ZA_GRUPU = 5;
    public static final int NAJVISE_ZA_STUDENTA = 5;
    public static final int NAJVISE_ZA_MCP = 50;
    /** Zatvorena anketa sa prikazanim rezultatom ostaje na projektoru i panelu najviše 30 min. */
    public static final Duration AKTIVNA_POSLE = Duration.ofMinutes(30);
    static final Duration PUSH_NA_CASU = Duration.ofMinutes(15);
    static final Duration PUSH_GRUPA_NAJDUZE = Duration.ofHours(24);

    public static final Set<String> SORT_POLJA = Set.of("otvoreno");
    public static final Sort PODRAZUMEVANI_SORT = Sort.by(Sort.Order.desc("otvoreno"));

    public static final String NIJE_PRONADJENA = "Anketa nije pronađena.";
    public static final String NIJE_ZA_TEBE = "Ova anketa nije za tebe.";
    public static final String ZATVORENA = "Anketa je zatvorena.";
    public static final String IZABERI_PREDAVANJE = "Izaberi predavanje.";
    public static final String PREDAVANJE_NIJE_PRONADJENO = "Predavanje nije pronađeno.";
    public static final String PREDAVANJE_NIJE_U_TOKU = "Predavanje nije u toku.";
    public static final String PREDAVANJE_BEZ_GRUPE = "Predavanje nema grupu.";
    public static final String IZABERI_GRUPU = "Izaberi grupu.";
    public static final String GRUPA_NIJE_PRONADJENA = "Grupa nije pronađena.";
    public static final String DRUGA_GRUPA = "Grupa nije grupa predavanja.";
    public static final String IZABERI_ROK = "Izaberi rok.";
    public static final String ROK = "Rok mora biti u narednih 14 dana.";
    public static final String PREVISE_ZA_GRUPU = "Grupa već ima " + NAJVISE_OTVORENIH_ZA_GRUPU
            + " otvorenih anketa. Zatvori neku pre nove.";
    public static final String SABLON_NIJE_PRONADJEN = "Šablon ankete nije pronađen.";
    public static final String NEMA_ZIVOG = "Nema predavanja u toku: navedi predavanjeId ili pokreni anketu za grupu.";
    public static final String PREDAVANJE_ILI_GRUPA = "Izaberi predavanje ili grupu.";

    /** Živo predavanje za Kloda (podrazumevano predavanje ankete na času): bez ičega o studentima. */
    private record ZivoPredavanje(Long id, String predmet, String grupa) {
    }

    private final AnketaRepository ankete;
    private final AnketaGlasRepository glasovi;
    private final AnketaSablonRepository sabloni;
    private final PredavanjeRepository predavanja;
    private final GrupaRepository grupe;
    private final StudentskaObavestenja obavestenja;
    private final PushPosiljalac posiljalac;
    private final Clock clock;

    // ---------------------------------------------------------------- nastavnik: pokretanje i izmene

    /**
     * Pokreće anketu (spec 4, "Pokretanje"): pitanje i odgovori po pravilima, {@code NA_CASU} na predavanju koje je u toku
     * (prethodna otvorena anketa na času istog predavanja se zatvara), {@code GRUPA} do roka u narednih 14 dana (najviše
     * {@value #NAJVISE_OTVORENIH_ZA_GRUPU} otvorenih po grupi). Šablon dobija brojač pokretanja, a sam se ne menja. U istoj
     * transakciji red zvona po primaocu ({@code ANKETA_CAS} ili {@code ANKETA}); push posle commit-a.
     */
    public AnketaDetails pokreni(PokreniAnketuCmd cmd, IzvorAnkete izvor) {
        String pitanje = AnketaPravila.pitanje(cmd.pitanje());
        List<String> odgovori = AnketaPravila.odgovori(cmd.opcije());
        PublikaAnkete publika = cmd.publika() == null ? PublikaAnkete.NA_CASU : cmd.publika();
        LocalDateTime sada = sada();
        Anketa a = new Anketa();
        a.setPitanje(pitanje);
        a.setOpcije(odgovori);
        a.setViseOdgovora(Boolean.TRUE.equals(cmd.viseOdgovora()));
        a.setPublika(publika);
        a.setOtvoreno(sada);
        a.setIzvor(izvor == null ? IzvorAnkete.NASTAVNIK : izvor);
        Set<Long> primaoci;
        if (publika == PublikaAnkete.NA_CASU) {
            Integer trajanje = AnketaPravila.trajanje(cmd.trajanjeSekunde());
            if (cmd.predavanjeId() == null) throw new SystemException(IZABERI_PREDAVANJE, HttpStatus.BAD_REQUEST);
            Predavanje p = predavanja.findByIdForUpdate(cmd.predavanjeId())
                    .orElseThrow(() -> new SystemException(PREDAVANJE_NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
            if (Boolean.TRUE.equals(p.getZavrseno())) throw new SystemException(PREDAVANJE_NIJE_U_TOKU, HttpStatus.CONFLICT);
            if (p.getGrupa() == null) throw new SystemException(PREDAVANJE_BEZ_GRUPE, HttpStatus.CONFLICT);
            a.setPredavanjeId(p.getId());
            a.setGrupaId(p.getGrupa().getId());
            a.setRok(trajanje == null ? null : sada.plusSeconds(trajanje));
            int zatvoreno = ankete.zatvoriNaCasu(p.getId(), sada);
            if (zatvoreno > 0) log.info("Nova anketa na času zatvara prethodnu: predavanje={}, zatvoreno={}", p.getId(), zatvoreno);
            primaoci = new LinkedHashSet<>(ankete.cekirani(p.getId()));
        } else {
            Long grupaId = cmd.grupaId();
            if (cmd.predavanjeId() != null) {
                Predavanje p = predavanja.findById(cmd.predavanjeId())
                        .orElseThrow(() -> new SystemException(PREDAVANJE_NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
                Long grupaPredavanja = p.getGrupa() == null ? null : p.getGrupa().getId();
                if (grupaId == null) grupaId = grupaPredavanja;
                else if (grupaPredavanja != null && !grupaPredavanja.equals(grupaId)) {
                    throw new SystemException(DRUGA_GRUPA, HttpStatus.BAD_REQUEST);
                }
                a.setPredavanjeId(p.getId());
            }
            if (grupaId == null) throw new SystemException(IZABERI_GRUPU, HttpStatus.BAD_REQUEST);
            if (!grupe.existsById(grupaId)) throw new SystemException(GRUPA_NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
            if (cmd.rok() == null) throw new SystemException(IZABERI_ROK, HttpStatus.BAD_REQUEST);
            LocalDateTime rok = cmd.rok().truncatedTo(ChronoUnit.MICROS);
            if (!rok.isAfter(sada) || rok.isAfter(sada.plus(AnketaPravila.MAX_ROK))) {
                throw new SystemException(ROK, HttpStatus.BAD_REQUEST);
            }
            if (ankete.brojOtvorenihZaGrupu(grupaId, sada) >= NAJVISE_OTVORENIH_ZA_GRUPU) {
                throw new SystemException(PREVISE_ZA_GRUPU, HttpStatus.CONFLICT);
            }
            a.setGrupaId(grupaId);
            a.setRok(rok);
            primaoci = new LinkedHashSet<>(ankete.studentiGrupeIPredavanja(grupaId, a.getPredavanjeId()));
        }
        if (cmd.sablonId() != null) {
            if (sabloni.pokrenut(cmd.sablonId(), sada) == 0) {
                throw new SystemException(SABLON_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
            }
            a.setSablonId(cmd.sablonId());
        }
        a = ankete.saveAndFlush(a);
        obavesti(a, primaoci, sada);
        log.info("Anketa pokrenuta: id={}, publika={}, predavanje={}, grupa={}, izvor={}, primalaca={}", a.getId(),
                publika, a.getPredavanjeId(), a.getGrupaId(), a.getIzvor(), primaoci.size());
        return detalji(List.of(a), sada).getFirst();
    }

    /**
     * Pokretanje za Kloda (MCP {@code pokreni_anketu}, {@code izvor = CLAUDE}): sa {@code sablonId} se polja koja nisu
     * poslata uzimaju iz šablona (i podrazumevani rok za grupu); anketa na času bez predavanja ide na živo predavanje kad je
     * ono tačno jedno (danas, nezavršeno). {@code rokSati} (ili {@code rokSati} šablona) daje rok od sada kad {@code rok}
     * nije poslat. Ostalo kao {@link #pokreni}.
     */
    public AnketaDetails pokreniZaKloda(PokreniAnketuCmd cmd, Integer rokSati) {
        String pitanje = cmd.pitanje();
        List<String> opcije = cmd.opcije();
        Boolean vise = cmd.viseOdgovora();
        PublikaAnkete publika = cmd.publika();
        Integer trajanje = cmd.trajanjeSekunde();
        Integer sati = rokSati;
        if (cmd.sablonId() != null) {
            AnketaSablon s = sabloni.findById(cmd.sablonId())
                    .orElseThrow(() -> new SystemException(SABLON_NIJE_PRONADJEN, HttpStatus.NOT_FOUND));
            if (pitanje == null) pitanje = s.getPitanje();
            if (opcije == null) opcije = s.getOpcije();
            if (vise == null) vise = s.isViseOdgovora();
            if (publika == null) publika = s.getPublika();
            if (trajanje == null && publika == PublikaAnkete.NA_CASU) trajanje = s.getTrajanjeSekunde();
            if (sati == null && publika == PublikaAnkete.GRUPA) sati = s.getRokSati();
        }
        if (publika == null) publika = PublikaAnkete.NA_CASU;
        Long predavanjeId = cmd.predavanjeId();
        if (publika == PublikaAnkete.NA_CASU && predavanjeId == null) {
            predavanjeId = jedinoZivo().id();
        }
        LocalDateTime rok = cmd.rok();
        if (publika == PublikaAnkete.GRUPA && rok == null && sati != null) {
            rok = sada().plusHours(AnketaPravila.rokSati(sati));
        }
        return pokreni(new PokreniAnketuCmd(cmd.sablonId(), pitanje, opcije, vise, publika, predavanjeId, cmd.grupaId(),
                trajanje, rok), IzvorAnkete.CLAUDE);
    }

    /** Zatvara otvorenu anketu ({@code zatvoreno = sada}); već zatvorena ostaje kakva jeste (idempotentno). */
    public AnketaDetails zatvori(Long id) {
        Anketa a = zakljucaj(id);
        LocalDateTime sada = sada();
        if (stanje(a, sada).otvorena()) {
            a.setZatvoreno(sada);
            ankete.saveAndFlush(a);
        }
        return detalji(List.of(a), sada).getFirst();
    }

    /** Rezultat studentima i projektoru; sme i dok je anketa otvorena (trake se tada pomeraju uživo). */
    public AnketaDetails prikaziRezultat(Long id, boolean prikazan) {
        Anketa a = zakljucaj(id);
        if (a.isRezultatPrikazan() != prikazan) {
            a.setRezultatPrikazan(prikazan);
            ankete.saveAndFlush(a);
        }
        return detalji(List.of(a), sada()).getFirst();
    }

    /** Briše anketu iz istorije (glasovi kaskadno u bazi; redovi zvona ostaju jer su zamrznuti). */
    public void obrisi(Long id) {
        Anketa a = zakljucaj(id);
        ankete.delete(a);
        ankete.flush();
        log.info("Anketa obrisana: id={}", id);
    }

    /**
     * Kraj predavanja ({@code PredavanjeService.zavrsiPredavanje}, ista transakcija, red predavanja je već zaključan):
     * otvorene ankete na času tog predavanja dobijaju pravo vreme zatvaranja. Ankete za grupu traju do roka.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int zatvoriNaKrajuPredavanja(Long predavanjeId) {
        return ankete.zatvoriNaCasu(predavanjeId, sada());
    }

    // ---------------------------------------------------------------- nastavnik: čitanje

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public AnketaDetails detalji(Long id) {
        Anketa a = ankete.findById(id).orElseThrow(AnketaService::nema);
        return detalji(List.of(a), sada()).getFirst();
    }

    /**
     * Za projektor i panel: otvorene ankete predavanja i/ili grupe i zatvorene sa prikazanim rezultatom u poslednjih
     * 30 min, najnovije prve. Bez oba parametra 400.
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<AnketaDetails> aktivne(Long predavanjeId, Long grupaId) {
        if (predavanjeId == null && grupaId == null) throw new SystemException(PREDAVANJE_ILI_GRUPA, HttpStatus.BAD_REQUEST);
        LocalDateTime sada = sada();
        List<Anketa> kandidati = ankete.zaPredavanjeIliGrupu(predavanjeId, grupaId,
                sada.minus(AnketaPravila.MAX_ROK).minus(AKTIVNA_POSLE));
        Map<Long, Boolean> zavrseno = zavrsenaPredavanja(kandidati);
        List<Anketa> aktivne = new ArrayList<>();
        for (Anketa a : kandidati) {
            Stanje s = AnketaPravila.stanje(a, zavrseno.get(a.getPredavanjeId()), sada);
            if (s.otvorena() || (a.isRezultatPrikazan() && s.zatvoreno() != null
                    && !s.zatvoreno().plus(AKTIVNA_POSLE).isBefore(sada))) {
                aktivne.add(a);
            }
        }
        return detalji(aktivne, sada);
    }

    /** Istorija (paging po konvenciji, sort {@code otvoreno desc}; {@code pageable} je prošao {@code PageableUtil.proveri}). */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public PagedModel<AnketaInfo> istorija(Long predavanjeId, Long grupaId, Pageable pageable) {
        Page<Anketa> strana = ankete.istorija(predavanjeId, grupaId, pageable);
        List<AnketaDetails> d = detalji(strana.getContent(), sada());
        List<AnketaInfo> redovi = d.stream().map(x -> new AnketaInfo(x.id(), x.pitanje(), x.publika(), x.otvoreno(),
                x.zatvoreno(), x.otvorena(), x.brojGlasalo(), x.brojMogucih(), AnketaPravila.pobednik(x.opcije(), x.glasovi()),
                x.predmetNaziv(), x.grupaNaziv())).toList();
        return new PagedModel<>(new PageImpl<>(redovi, pageable, strana.getTotalElements()));
    }

    /** Istorija za Kloda: najviše {@value #NAJVISE_ZA_MCP} najnovijih, sa brojevima po odgovoru (bez ikoga pojedinačno). */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<AnketaDetails> istorijaZaMcp(Long predmetId, Long grupaId, Long predavanjeId) {
        return detalji(ankete.istorijaZaMcp(predmetId, grupaId, predavanjeId, PageRequest.of(0, NAJVISE_ZA_MCP)), sada());
    }

    /** Današnja nezavršena predavanja (za Kloda), najnovija prva. */
    private List<ZivoPredavanje> zivaPredavanja() {
        return ankete.zivaPredavanja(LocalDate.now(clock)).stream()
                .map(p -> new ZivoPredavanje(p.getId(), p.getPredmet().getNaziv(),
                        p.getGrupa() == null ? null : p.getGrupa().getNaziv()))
                .toList();
    }

    // ---------------------------------------------------------------- student

    /**
     * Ankete za koje student sme da glasa (spec 5, "Skup za studenta"): otvorene i zatvorene sa prikazanim rezultatom
     * (na času dok predavanje traje i najviše 2 h, za grupu 24 h posle zatvaranja), najnovije prve, najviše
     * {@value #NAJVISE_ZA_STUDENTA}; sa njegovim glasom i rezultatom samo kad je prikazan.
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<AnketaStudentu> zaStudenta(long studentId) {
        LocalDateTime sada = sada();
        Long grupa = ankete.grupaStudenta(studentId).orElse(null);
        List<Anketa> kandidati = ankete.kandidatiZaStudenta(studentId, grupa,
                sada.minus(AnketaPravila.NAJDUZE_NA_CASU).minus(AnketaPravila.REZULTAT_NA_CASU),
                sada.minus(AnketaPravila.MAX_ROK).minus(AnketaPravila.REZULTAT_GRUPA));
        if (kandidati.isEmpty()) return List.of();
        Map<Long, Boolean> zavrseno = zavrsenaPredavanja(kandidati);
        List<Anketa> vidi = new ArrayList<>();
        Map<Long, Stanje> stanja = new HashMap<>();
        for (Anketa a : kandidati) {
            Boolean kraj = zavrseno.get(a.getPredavanjeId());
            Stanje s = AnketaPravila.stanje(a, kraj, sada);
            if (AnketaPravila.vidiStudent(a, s, kraj, sada)) {
                vidi.add(a);
                stanja.put(a.getId(), s);
                if (vidi.size() == NAJVISE_ZA_STUDENTA) break;
            }
        }
        return studentu(vidi, stanja, studentId, sada);
    }

    /**
     * Glas studenta (spec 4): nepostojeća 404 {@value #NIJE_PRONADJENA}, anketa koja nije za njega 403
     * {@value #NIJE_ZA_TEBE}, zatvorena 409 {@value #ZATVORENA}, neispravan izbor 400. Ponovni glas zamenjuje stari
     * (jedan red pod jedinstvenim ključem i kad stignu dva paralelna zahteva).
     */
    public AnketaStudentu glasaj(long studentId, Long anketaId, GlasCmd cmd) {
        Anketa a = ankete.findByIdForShare(anketaId).orElseThrow(AnketaService::nema);
        if (!smeDaGlasa(a, studentId)) throw new SystemException(NIJE_ZA_TEBE, HttpStatus.FORBIDDEN);
        LocalDateTime sada = sada();
        Stanje s = stanje(a, sada);
        if (!s.otvorena()) throw new SystemException(ZATVORENA, HttpStatus.CONFLICT);
        List<Integer> izbor = AnketaPravila.glas(cmd == null ? null : cmd.opcije(), a.getOpcije().size(), a.isViseOdgovora());
        glasovi.upisi(a.getId(), studentId, AnketaPravila.uKolonu(izbor), sada);
        return studentu(List.of(a), Map.of(a.getId(), s), studentId, sada).getFirst();
    }

    // ---------------------------------------------------------------- pomoćno

    /** Jedino živo predavanje (Klod bez {@code predavanjeId}); nijedno 409, više njih 400 sa spiskom. */
    private ZivoPredavanje jedinoZivo() {
        List<ZivoPredavanje> ziva = zivaPredavanja();
        if (ziva.isEmpty()) throw new SystemException(NEMA_ZIVOG, HttpStatus.CONFLICT);
        if (ziva.size() > 1) {
            StringJoiner j = new StringJoiner("; ");
            ziva.forEach(z -> j.add(z.id() + ": " + z.predmet() + (z.grupa() == null ? "" : ", " + z.grupa())));
            throw new SystemException("U toku je više predavanja (" + j + "): navedi predavanjeId.", HttpStatus.BAD_REQUEST);
        }
        return ziva.getFirst();
    }

    private boolean smeDaGlasa(Anketa a, long studentId) {
        boolean cekiran = a.getPredavanjeId() != null && ankete.cekiran(a.getPredavanjeId(), studentId);
        if (a.getPublika() == PublikaAnkete.NA_CASU) return cekiran;
        return cekiran || ankete.grupaStudenta(studentId).map(a.getGrupaId()::equals).orElse(false);
    }

    private Stanje stanje(Anketa a, LocalDateTime sada) {
        return AnketaPravila.stanje(a, zavrsenaPredavanja(List.of(a)).get(a.getPredavanjeId()), sada);
    }

    /** Predavanje -> završeno; predavanja koje više nema nema ni u mapi (stanje ga tada vidi kao {@code null}). */
    private Map<Long, Boolean> zavrsenaPredavanja(Collection<Anketa> lista) {
        Set<Long> ids = new HashSet<>();
        lista.forEach(a -> { if (a.getPredavanjeId() != null) ids.add(a.getPredavanjeId()); });
        Map<Long, Boolean> m = new HashMap<>();
        if (ids.isEmpty()) return m;
        for (Object[] r : ankete.predavanja(ids)) {
            m.put(((Number) r[0]).longValue(), Boolean.TRUE.equals(r[1]));
        }
        return m;
    }

    /** Nastavnički pogled na više anketa odjednom: po jedan upit za predavanja, grupe, glasove i broj mogućih. */
    private List<AnketaDetails> detalji(List<Anketa> lista, LocalDateTime sada) {
        if (lista.isEmpty()) return List.of();
        Set<Long> ids = new LinkedHashSet<>();
        Set<Long> predavanjaIds = new HashSet<>();
        Set<Long> grupeIds = new HashSet<>();
        for (Anketa a : lista) {
            ids.add(a.getId());
            if (a.getPredavanjeId() != null) predavanjaIds.add(a.getPredavanjeId());
            grupeIds.add(a.getGrupaId());
        }
        Map<Long, Boolean> zavrseno = new HashMap<>();
        Map<Long, String> predmet = new HashMap<>();
        if (!predavanjaIds.isEmpty()) {
            for (Object[] r : ankete.predavanja(predavanjaIds)) {
                Long id = ((Number) r[0]).longValue();
                zavrseno.put(id, Boolean.TRUE.equals(r[1]));
                predmet.put(id, (String) r[2]);
            }
        }
        Map<Long, String> grupa = new HashMap<>();
        for (Object[] r : ankete.naziviGrupa(grupeIds)) grupa.put(((Number) r[0]).longValue(), (String) r[1]);
        Map<Long, List<String>> glasoviPo = glasoviPoAnketi(ids);
        Map<Long, Integer> mogucih = new HashMap<>();
        for (Object[] r : glasovi.brojMogucih(ids)) mogucih.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
        long sadaMs = clock.millis();
        List<AnketaDetails> rez = new ArrayList<>(lista.size());
        for (Anketa a : lista) {
            Stanje s = AnketaPravila.stanje(a, zavrseno.get(a.getPredavanjeId()), sada);
            List<String> g = glasoviPo.getOrDefault(a.getId(), List.of());
            rez.add(new AnketaDetails(a.getId(), a.getSablonId(), a.getPitanje(), a.getOpcije(), a.isViseOdgovora(),
                    a.getPublika(), a.getPredavanjeId(), a.getGrupaId(), grupa.get(a.getGrupaId()),
                    predmet.get(a.getPredavanjeId()), a.getOtvoreno(), a.getRok(), ms(a.getRok()), s.zatvoreno(),
                    s.otvorena(), a.isRezultatPrikazan(), a.getIzvor(), AnketaPravila.poOdgovoru(a.getOpcije().size(), g),
                    g.size(), mogucih.getOrDefault(a.getId(), 0), sadaMs));
        }
        return rez;
    }

    private List<AnketaStudentu> studentu(List<Anketa> lista, Map<Long, Stanje> stanja, long studentId, LocalDateTime sada) {
        if (lista.isEmpty()) return List.of();
        Set<Long> ids = new LinkedHashSet<>();
        Set<Long> saRezultatom = new HashSet<>();
        for (Anketa a : lista) {
            ids.add(a.getId());
            if (a.isRezultatPrikazan()) saRezultatom.add(a.getId());
        }
        Map<Long, List<Integer>> moji = new HashMap<>();
        for (AnketaGlas g : glasovi.findAllByStudentIdAndAnketaIdIn(studentId, ids)) {
            moji.put(g.getAnketaId(), AnketaPravila.izKolone(g.getOpcije()));
        }
        Map<Long, List<String>> glasoviPo = saRezultatom.isEmpty() ? Map.of() : glasoviPoAnketi(saRezultatom);
        long sadaMs = clock.millis();
        List<AnketaStudentu> rez = new ArrayList<>(lista.size());
        for (Anketa a : lista) {
            RezultatAnkete rezultat = null;
            if (a.isRezultatPrikazan()) {
                List<String> g = glasoviPo.getOrDefault(a.getId(), List.of());
                rezultat = new RezultatAnkete(AnketaPravila.poOdgovoru(a.getOpcije().size(), g), g.size());
            }
            rez.add(new AnketaStudentu(a.getId(), a.getPitanje(), a.getOpcije(), a.isViseOdgovora(), a.getPublika(),
                    ms(a.getRok()), stanja.get(a.getId()).otvorena(), moji.get(a.getId()), rezultat, sadaMs));
        }
        return rez;
    }

    private Map<Long, List<String>> glasoviPoAnketi(Collection<Long> ids) {
        Map<Long, List<String>> m = new HashMap<>();
        for (Object[] r : glasovi.opcijeGlasova(ids)) {
            m.computeIfAbsent(((Number) r[0]).longValue(), k -> new ArrayList<>()).add((String) r[1]);
        }
        return m;
    }

    /**
     * Red zvona po primaocu u ovoj transakciji (ključ {@code ANKETA:<id>}), push posle commit-a na niti push izvršioca,
     * samo studentima koji nisu isključili {@code push_ankete}. Na času: hitno, TTL do roka ili 15 min; za grupu: obično,
     * TTL do roka, najviše 24 h. Tekst samo iz Novicinog pitanja i roka.
     */
    private void obavesti(Anketa a, Set<Long> primaoci, LocalDateTime sada) {
        if (primaoci.isEmpty()) return;
        boolean naCasu = a.getPublika() == PublikaAnkete.NA_CASU;
        String kljuc = "ANKETA:" + a.getId();
        PushPoruka poruka = naCasu ? PushTekstovi.anketaNaCasu(a.getPitanje())
                : PushTekstovi.anketaGrupe(a.getId(), a.getPitanje(), a.getRok());
        Set<Long> novi = obavestenja.upisi(primaoci, naCasu ? VrstaStudentskogObavestenja.ANKETA_CAS
                : VrstaStudentskogObavestenja.ANKETA, kljuc, poruka);
        Duration ttl;
        if (naCasu) {
            ttl = a.getRok() == null ? PUSH_NA_CASU : Duration.between(sada, a.getRok());
        } else {
            Duration doRoka = Duration.between(sada, a.getRok());
            ttl = doRoka.compareTo(PUSH_GRUPA_NAJDUZE) > 0 ? PUSH_GRUPA_NAJDUZE : doRoka;
        }
        Duration konacan = ttl;
        posiljalac.uPozadiniPosleCommita(() -> obavestenja.posalji(kljuc, novi, Prekidac.PUSH_ANKETE, konacan, naCasu));
    }

    private Anketa zakljucaj(Long id) {
        return ankete.findByIdForUpdate(id).orElseThrow(AnketaService::nema);
    }

    private Long ms(LocalDateTime t) {
        return AnketaPravila.ms(t, clock.getZone());
    }

    /** Sada, na mikrosekunde (kolone su {@code datetime(6)}): vreme u odgovoru je isto kao posle čitanja iz baze. */
    private LocalDateTime sada() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static SystemException nema() {
        return new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
    }
}
