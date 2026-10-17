package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.*;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.validation.BankaPP;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

/**
 * Varijante zadataka banke (spec 2.3, 2.4): CRUD, statusi, render sa kešom po hešu, prilozi i render-proba.
 * <p>
 * <b>Render:</b> pri svakom upisu (kreiranje, izmena, duplikat) servis računa heš ulaza rendera
 * ({@link RenderVarijante#hash}); renderuje sinhrono u istom zahtevu, osim kad je heš jednak sopstvenom prethodnom
 * ({@code render_hash} te varijante, nikad tuđem) i render je {@code OK}. Uspeh upisuje nove SVG medije ({@code RENDER_SVG},
 * {@code zadatak-<id>.svg}, {@code resenje-<id>.svg}) pa tek onda briše stare; neuspeh je stanje, ne izuzetak
 * ({@code GRESKA}, greška u {@code render_greska} kao {@code <deo>:<linija>: <poruka>}, stari SVG ostaje), a AKTIVNA tada pada u
 * NACRT (aktivna uvek ima uspeo render). Render servis koji ne radi ({@link RenderNedostupan}, 503) poništava ceo zahtev:
 * ništa se ne upisuje (ni nova varijanta ni izmena), front zadržava unos i ponavlja.
 * {@link MedijService#obrisi} briše fajl odmah, a red pri flush-u, posle upisa koji ga više ne pominje; pad commit-a posle
 * toga ostavlja red medija bez fajla (servira se kao 404), što se prihvata.
 * <p>
 * <b>Zaključavanje</b> (ugovor iz {@link GrupaZadatakaService}): pre dodavanja ili izmene varijante red grupe
 * ({@code GrupaZadatakaRepository.findByIdForUpdate}), pa red varijante ({@code findByIdForUpdate}); drži najviše jednu
 * grupu, nikad ne zaključava predmet i nikad ne upisuje kolone {@code grupe_zadataka}. Pre ključa se čita samo id grupe
 * ({@code findGrupaId}) i proverava sadržaj komande; šema polja grupe se čita posle ključa. Ključevi traju i dok render
 * radi (do dva poziva servisa), pa drugi upis iste grupe čeka.
 * <p>
 * Izmene rade u READ COMMITTED, kao {@link GrupaZadatakaService}. Proba radi bez transakcije (ne drži vezu ka bazi dok
 * renderuje); render cele banke radi svaku varijantu u zasebnoj transakciji, pa ključ grupe traje samo dok se ta
 * varijanta renderuje. Grupa bez težine ne prima varijante (409), kao što ni {@code GrupaZadatakaService.izmeni} ne
 * dozvoljava grupu sa varijantama bez težine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class VarijantaService {

    static final String NIJE_PRONADJENA = "Varijanta nije pronađena.";
    static final String IMA_PODGRUPE = "Grupa sa podgrupama ne može imati varijante.";
    static final String RENDER_NIJE_USPEO = "Varijanta se odobrava tek kad je render uspeo.";
    static final String PREVISE_PROBA = "Previše proba u isto vreme.";
    static final String ODGOVORI_PREVELIKI = "Odgovori su preveliki.";
    static final String PREVISE_PRILOGA = "Najviše 10 priloga.";
    static final String REZERVISANO_IME = "Ime priloga 'dokument' je rezervisano.";

    static final int MAX_PROBA = 4;
    /** Render cele banke: najviše ovoliko varijanti po pozivu ... */
    static final int MAX_RENDERA_PO_POZIVU = 200;
    /** ... i ne počinje novu posle ovoliko vremena, da odgovor stigne pre isteka proksija (front nginx čeka 60 s). */
    static final Duration MAX_TRAJANJE_RENDERA_SVIH = Duration.ofSeconds(40);
    private static final int MAX_PRILOGA = 10;
    /** Kolona {@code odgovori} je MySQL {@code text}: najviše 65 535 bajtova. */
    private static final int MAX_ODGOVORI_BAJTOVA = 65_535;
    /** Render servis piše {@code dokument.tex}, {@code .pdf} i {@code .log}: prilog tog imena bi ih pregazio. */
    private static final String REZERVISANO = "dokument";

    private final GrupaZadatakaRepository grupaRepository;
    private final VarijantaRepository varijantaRepository;
    private final PredmetRepository predmetRepository;
    private final MedijRepository medijRepository;
    private final MedijService medijService;
    private final RenderVarijante renderVarijante;
    private final GrupaZadatakaService grupaZadatakaService;
    private final PlatformTransactionManager transakcije;
    private final EntityManager em;
    private final Clock clock;
    /** Render-proba: najviše {@value #MAX_PROBA} istovremeno po backendu, preko toga 429 (ne čeka se). */
    private final Semaphore probe = new Semaphore(MAX_PROBA);

    // ---------------------------------------------------------------- čitanje

    /** Varijante grupe po {@code rb}; {@code status == null} = sve. */
    @Transactional(readOnly = true)
    public List<VarijantaInfo> lista(Long grupaId, StatusVarijante status) {
        grupaRepository.findById(grupaId).orElseThrow(VarijantaService::grupaNijePronadjena);
        return varijantaRepository.lista(grupaId, status).stream().map(VarijantaService::info).toList();
    }

    @Transactional(readOnly = true)
    public VarijantaDetails detalji(Long id) {
        return details(varijantaRepository.findById(id).orElseThrow(VarijantaService::nijePronadjena));
    }

    /** Da li je varijanta ikad dodeljena na času; faza 1 nema dodela, pa je uvek {@code false} (faza 2: {@code zadatak_dodele}). */
    public boolean koriscenoNaCasu(Long varijantaId) {
        return false;
    }

    // ---------------------------------------------------------------- upis

    /**
     * Nova varijanta na kraj grupe ({@code rb} = najveći + 1), NACRT, {@code izvor} iz komande ili RUCNO; render odmah.
     * Grupa mora biti list (409 za grupu sa podgrupama) sa težinom (409), odgovori moraju pokriti šemu polja grupe.
     */
    public VarijantaDetails kreiraj(Long grupaId, VarijantaCmd cmd) {
        Sadrzaj s = proveren(cmd);
        GrupaZadataka g = zakljucajGrupu(grupaId);
        if (grupaRepository.countByRoditeljId(grupaId) > 0) {
            throw new SystemException(IMA_PODGRUPE, HttpStatus.CONFLICT);
        }
        if (g.getTezina() == null) {
            throw new SystemException(GrupaZadatakaService.TEZINA_OBAVEZNA, HttpStatus.CONFLICT);
        }
        proveriOdgovore(g, s.odgovori());

        LocalDateTime sada = sada();
        Varijanta v = new Varijanta();
        v.setGrupa(g);
        v.setRb(varijantaRepository.maxRb(grupaId) + 1);
        primeni(v, s);
        v.setStatus(StatusVarijante.NACRT);
        v.setIzvor(cmd.izvor() == null ? IzvorVarijante.RUCNO : cmd.izvor());
        v.setRenderStatus(RenderStatus.CEKA);
        v.setKreirano(sada);
        v.setIzmenjeno(sada);
        v = varijantaRepository.save(v);   // id je deo naziva SVG medija
        renderujAkoTreba(v, false);
        log.info("Varijanta kreirana: id={}, grupa={}, izvor={}, render={}", v.getId(), grupaId, v.getIzvor(), v.getRenderStatus());
        return details(v);
    }

    /**
     * Puna zamena naziva, teksta, rešenja, odgovora i priloga (izvor ostaje). Renderuje samo kad se ulaz rendera
     * promenio ili poslednji render nije uspeo; AKTIVNA ostaje AKTIVNA samo ako render prođe.
     */
    public VarijantaDetails izmeni(Long id, VarijantaCmd cmd) {
        Sadrzaj s = proveren(cmd);
        Varijanta v = zakljucaj(id);
        proveriOdgovore(v.getGrupa(), s.odgovori());
        primeni(v, s);
        v.setIzmenjeno(sada());
        boolean renderovano = renderujAkoTreba(v, false);
        log.info("Varijanta izmenjena: id={}, render={} ({})", id, v.getRenderStatus(), renderovano ? "nov" : "keš");
        return details(v);
    }

    /** Briše varijantu (prilozi kaskadom; mediji priloga ostaju, duplikat ih može deliti), pa njene SVG medije. */
    public void obrisi(Long id) {
        Varijanta v = zakljucaj(id);
        if (koriscenoNaCasu(id)) {
            throw new SystemException(GrupaZadatakaService.KORISCENO_NA_CASU, HttpStatus.CONFLICT);
        }
        List<String> svg = svgIds(v);
        varijantaRepository.delete(v);
        varijantaRepository.flush();
        svg.forEach(medijService::obrisi);
        log.info("Varijanta obrisana: id={}", id);
    }

    /**
     * U AKTIVNU (i iz ARHIVIRANE): render mora biti uspeo (409) i odgovori potpuni po trenutnoj šemi grupe (400, poruke
     * {@code BankaPP.proveriOdgovore}). Već aktivna ostaje kakva je.
     */
    public VarijantaDetails odobri(Long id) {
        Varijanta v = zakljucaj(id);
        if (v.getRenderStatus() != RenderStatus.OK) {
            throw new SystemException(RENDER_NIJE_USPEO, HttpStatus.CONFLICT);
        }
        proveriOdgovore(v.getGrupa(), PoljaJson.odgovoriIzKolone(v.getOdgovori()));
        promeniStatus(v, StatusVarijante.AKTIVNA);
        return details(v);
    }

    public VarijantaDetails arhiviraj(Long id) {
        Varijanta v = zakljucaj(id);
        promeniStatus(v, StatusVarijante.ARHIVIRANA);
        return details(v);
    }

    public VarijantaDetails vratiUNacrt(Long id) {
        Varijanta v = zakljucaj(id);
        promeniStatus(v, StatusVarijante.NACRT);
        return details(v);
    }

    /** Render bez obzira na keš (npr. posle pada servisa ili izmene paketa u kontejneru). */
    public VarijantaDetails render(Long id) {
        Varijanta v = zakljucaj(id);
        renderujAkoTreba(v, true);
        log.info("Varijanta renderovana: id={}, render={}", id, v.getRenderStatus());
        return details(v);
    }

    /**
     * Kopija na kraj iste grupe: NACRT, isti naziv, sadržaj, odgovori, izvor i prilozi (isti mediji), sopstveni render
     * (SVG medij pripada jednoj varijanti, pa se ne deli).
     */
    public VarijantaDetails dupliraj(Long id) {
        Varijanta izvor = zakljucaj(id);
        GrupaZadataka g = izvor.getGrupa();
        LocalDateTime sada = sada();
        Varijanta k = new Varijanta();
        k.setGrupa(g);
        k.setRb(varijantaRepository.maxRb(g.getId()) + 1);
        k.setNaziv(izvor.getNaziv());
        k.setTekst(izvor.getTekst());
        k.setResenje(izvor.getResenje());
        k.setOdgovori(izvor.getOdgovori());
        primeniPriloge(k, izvor.getPrilozi().stream().map(p -> new Prilog(p.getIme(), p.getMedij())).toList());
        k.setStatus(StatusVarijante.NACRT);
        k.setIzvor(izvor.getIzvor());
        k.setRenderStatus(RenderStatus.CEKA);
        k.setKreirano(sada);
        k.setIzmenjeno(sada);
        k = varijantaRepository.save(k);
        renderujAkoTreba(k, false);
        log.info("Varijanta duplirana: id={} -> {}, render={}", id, k.getId(), k.getRenderStatus());
        return details(k);
    }

    // ---------------------------------------------------------------- render bez čuvanja i cela banka

    /**
     * Render bez čuvanja, sa preambulom predmeta, za pregled u editoru (i za Claude-a kroz MCP). Ista pravila teksta,
     * rešenja i priloga kao upis; najviše {@value #MAX_PROBA} istovremeno (429 preko toga).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RenderProbaInfo proba(RenderProbaCmd cmd) {
        if (cmd == null) {
            throw new SystemException(GrupaZadatakaService.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        if (cmd.predmetId() == null) {
            throw new SystemException(GrupaZadatakaService.PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        String resenje = prazanUNull(cmd.resenje());
        BankaPP.proveriVarijantu(cmd.tekst(), resenje, null);
        RenderVarijante.proveriTelo(cmd.tekst(), resenje);
        List<PrilogCmd> prilozi = proveriPriloge(cmd.prilozi()).stream()
                .map(p -> new PrilogCmd(p.ime(), p.medij().getId())).toList();
        Predmet predmet = predmetRepository.findById(cmd.predmetId()).orElseThrow(VarijantaService::predmetNijePronadjen);
        if (!probe.tryAcquire()) {
            throw new SystemException(PREVISE_PROBA, HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            RenderVarijante.Ishod ishod = renderVarijante.renderuj(predmet.getLatexPreambula(), cmd.tekst(), resenje, prilozi);
            return new RenderProbaInfo(ishod.ok(), tekst(ishod.svg()), tekst(ishod.resenjeSvg()), ishod.greska(), ishod.deo());
        } finally {
            probe.release();
        }
    }

    /**
     * Ponovni render banke predmeta (posle izmene preambule): sinhrono, redom, svaka varijanta u svojoj transakciji
     * (ključ grupe pa varijante, kao upis), bez ključa predmeta. Prvo zastarele (heš sadržaja se razlikuje od poslednjeg
     * rendera ili render čeka), pa one čiji je poslednji render istog sadržaja pao; najviše
     * {@value #MAX_RENDERA_PO_POZIVU} po pozivu i nijedna nova posle {@link #MAX_TRAJANJE_RENDERA_SVIH}. {@code preostalo}
     * broji samo zastarele koje nisu stigle na red: pala varijanta istog sadržaja bi pala opet, pa front koji zove dok
     * je {@code preostalo > 0} uvek stane. 503 servisa prekida poziv; već renderovane ostaju upisane.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RenderujSveInfo renderujSve(Long predmetId) {
        if (predmetId == null) {
            throw new SystemException(GrupaZadatakaService.PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        TransactionTemplate tx = transakcija();
        Kandidati kandidati = tx.execute(status -> kandidati(predmetId));
        Instant rok = clock.instant().plus(MAX_TRAJANJE_RENDERA_SVIH);
        int obradjeno = 0;
        int renderovano = 0;
        for (Long id : kandidati.redom()) {
            if (obradjeno == MAX_RENDERA_PO_POZIVU || !clock.instant().isBefore(rok)) {
                break;
            }
            obradjeno++;
            if (Boolean.TRUE.equals(tx.execute(status -> renderujPoKljucu(id)))) {
                renderovano++;
            }
        }
        int preostalo = Math.max(0, kandidati.zastarelih() - obradjeno);
        log.info("Render banke predmeta {}: renderovano={}, preostalo={}", predmetId, renderovano, preostalo);
        return new RenderujSveInfo(renderovano, preostalo);
    }

    /** Id-jevi za render cele banke: prvih {@code zastarelih} su zastarele, ostatak pale istog sadržaja; obe grupe po id-ju. */
    private record Kandidati(List<Long> redom, int zastarelih) {
    }

    private Kandidati kandidati(Long predmetId) {
        Predmet predmet = predmetRepository.findById(predmetId).orElseThrow(VarijantaService::predmetNijePronadjen);
        List<Varijanta> sve = new ArrayList<>(varijantaRepository.svePredmeta(predmetId));
        sve.sort(Comparator.comparing(Varijanta::getId));
        List<Long> zastarele = new ArrayList<>();
        List<Long> pale = new ArrayList<>();
        for (Varijanta v : sve) {
            String hash = renderVarijante.hash(predmet.getLatexPreambula(), v.getTekst(), v.getResenje(), prilozi(v));
            if (!hash.equals(v.getRenderHash()) || v.getRenderStatus() == RenderStatus.CEKA) {
                zastarele.add(v.getId());
            } else if (v.getRenderStatus() != RenderStatus.OK) {
                pale.add(v.getId());
            }
        }
        return new Kandidati(Stream.concat(zastarele.stream(), pale.stream()).toList(), zastarele.size());
    }

    /** Jedna varijanta cele banke, u svojoj transakciji; {@code false} kad je u međuvremenu nestala ili je već sveža. */
    private boolean renderujPoKljucu(Long id) {
        // isti EntityManager traje ceo HTTP zahtev (open-in-view): bez ovoga bi ključ vratio entitet pročitan pre njega
        em.clear();
        Optional<Varijanta> v = zakljucajAkoPostoji(id);
        return v.isPresent() && renderujAkoTreba(v.get(), false);
    }

    // ---------------------------------------------------------------- render i SVG mediji

    /** Renderuje kad je {@code prisilno} ili keš ne važi; vraća da li je renderovano. */
    private boolean renderujAkoTreba(Varijanta v, boolean prisilno) {
        String preambula = v.getGrupa().getPredmet().getLatexPreambula();
        List<PrilogCmd> prilozi = prilozi(v);
        String hash = renderVarijante.hash(preambula, v.getTekst(), v.getResenje(), prilozi);
        if (!prisilno && hash.equals(v.getRenderHash()) && v.getRenderStatus() == RenderStatus.OK) {
            return false;
        }
        primeniRender(v, hash, renderVarijante.renderuj(preambula, v.getTekst(), v.getResenje(), prilozi));
        return true;
    }

    private void primeniRender(Varijanta v, String hash, RenderVarijante.Ishod ishod) {
        v.setRenderHash(hash);
        if (!ishod.ok()) {
            v.setRenderStatus(RenderStatus.GRESKA);
            v.setRenderGreska(RenderVarijante.uKolonu(ishod.deo(), ishod.greska()));
            if (v.getStatus() == StatusVarijante.AKTIVNA) {
                promeniStatus(v, StatusVarijante.NACRT);
            }
            return;   // stari SVG ostaje do sledećeg uspešnog rendera
        }
        List<String> stari = svgIds(v);
        v.setRenderSvg(sacuvajSvg(ishod.svg(), "zadatak-" + v.getId() + ".svg"));
        v.setRenderResenjeSvg(ishod.resenjeSvg() == null ? null : sacuvajSvg(ishod.resenjeSvg(), "resenje-" + v.getId() + ".svg"));
        v.setRenderStatus(RenderStatus.OK);
        v.setRenderGreska(null);
        stari.forEach(medijService::obrisi);
    }

    private Medij sacuvajSvg(byte[] svg, String naziv) {
        return medijRepository.getReferenceById(medijService.sacuvajRender(svg, naziv).id());
    }

    private static List<String> svgIds(Varijanta v) {
        return Stream.of(v.getRenderSvg(), v.getRenderResenjeSvg()).filter(Objects::nonNull).map(Medij::getId).toList();
    }

    private static List<PrilogCmd> prilozi(Varijanta v) {
        return v.getPrilozi().stream().map(p -> new PrilogCmd(p.getIme(), p.getMedij().getId())).toList();
    }

    // ---------------------------------------------------------------- provera komande

    /** Proverena komanda; sve se proverava pre zaključavanja, osim odgovora naspram šeme grupe. */
    private record Sadrzaj(String naziv, String tekst, String resenje, Map<String, OdgovorPolja> odgovori, String odgovoriJson,
                           List<Prilog> prilozi) {
    }

    private record Prilog(String ime, Medij medij) {
    }

    /** Naziv bez razmaka na krajevima (prazan = null); tekst i rešenje doslovno (brojevi linija), prazno rešenje = null. */
    private Sadrzaj proveren(VarijantaCmd cmd) {
        if (cmd == null) {
            throw new SystemException(GrupaZadatakaService.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        String naziv = prazanUNull(cmd.naziv()) == null ? null : cmd.naziv().strip();
        String resenje = prazanUNull(cmd.resenje());
        BankaPP.proveriVarijantu(cmd.tekst(), resenje, naziv);
        RenderVarijante.proveriTelo(cmd.tekst(), resenje);
        Map<String, OdgovorPolja> odgovori = cmd.odgovori() == null ? Map.of() : cmd.odgovori();
        String json = PoljaJson.uJson(odgovori);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_ODGOVORI_BAJTOVA) {
            throw new SystemException(ODGOVORI_PREVELIKI, HttpStatus.BAD_REQUEST);
        }
        return new Sadrzaj(naziv, cmd.tekst(), resenje, odgovori, json, proveriPriloge(cmd.prilozi()));
    }

    /**
     * Do {@value #MAX_PRILOGA} priloga, ime po {@code BankaPP.proveriImePriloga}, ne {@value #REZERVISANO}, jedinstveno;
     * medij postoji i LaTeX ga može uključiti: slika PNG ili JPEG (ne GIF, WebP) ili PDF prilog (ne render SVG).
     */
    private List<Prilog> proveriPriloge(List<PrilogCmd> prilozi) {
        List<PrilogCmd> lista = prilozi == null ? List.of() : prilozi;
        if (lista.size() > MAX_PRILOGA) {
            throw new SystemException(PREVISE_PRILOGA, HttpStatus.BAD_REQUEST);
        }
        Set<String> imena = new HashSet<>();
        List<Prilog> rezultat = new ArrayList<>();
        for (PrilogCmd p : lista) {
            if (p == null) {
                throw new SystemException(GrupaZadatakaService.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
            }
            BankaPP.proveriImePriloga(p.ime());
            if (REZERVISANO.equals(p.ime())) {
                throw new SystemException(REZERVISANO_IME, HttpStatus.BAD_REQUEST);
            }
            if (!imena.add(p.ime())) {
                throw new SystemException("Ime priloga '" + p.ime() + "' se ponavlja.", HttpStatus.BAD_REQUEST);
            }
            Medij medij = (p.medijId() == null ? Optional.<Medij>empty() : medijRepository.findById(p.medijId()))
                    .orElseThrow(() -> new SystemException("Prilog '" + p.ime() + "' nije pronađen.", HttpStatus.BAD_REQUEST));
            if (!zaLatex(medij)) {
                throw new SystemException(RenderVarijante.NEDOZVOLJEN_PRILOG, HttpStatus.BAD_REQUEST);
            }
            rezultat.add(new Prilog(p.ime(), medij));
        }
        return rezultat;
    }

    private static boolean zaLatex(Medij m) {
        return switch (m.getVrsta()) {
            case SLIKA -> "image/png".equals(m.getMime()) || "image/jpeg".equals(m.getMime());
            case PRILOG_PDF -> "application/pdf".equals(m.getMime());
            case RENDER_SVG -> false;
        };
    }

    /** Odgovori naspram šeme polja grupe; zove se posle ključa grupe. Grupa bez polja prima samo prazne odgovore. */
    private static void proveriOdgovore(GrupaZadataka g, Map<String, OdgovorPolja> odgovori) {
        BankaPP.proveriOdgovore(BankaPP.normalizujPolja(PoljaJson.izKolone(g.getPolja())), odgovori);
    }

    private static void primeni(Varijanta v, Sadrzaj s) {
        v.setNaziv(s.naziv());
        v.setTekst(s.tekst());
        v.setResenje(s.resenje());
        v.setOdgovori(s.odgovoriJson());
        primeniPriloge(v, s.prilozi());
    }

    /**
     * Prilozi po imenu: postojeći red istog imena dobija novi medij, nestali se brišu (orphanRemoval), novi se dodaju.
     * Red se ne briše pa ponovo dodaje, jer Hibernate upisuje nove pre brisanja starih ({@code UNIQUE(varijanta_id, ime)}).
     */
    private static void primeniPriloge(Varijanta v, List<Prilog> novi) {
        Map<String, Prilog> poImenu = new LinkedHashMap<>();
        novi.forEach(p -> poImenu.put(p.ime(), p));
        v.getPrilozi().removeIf(p -> !poImenu.containsKey(p.getIme()));
        for (VarijantaPrilog p : v.getPrilozi()) {
            p.setMedij(poImenu.remove(p.getIme()).medij());
        }
        for (Prilog p : poImenu.values()) {
            VarijantaPrilog vp = new VarijantaPrilog();
            vp.setVarijanta(v);
            vp.setIme(p.ime());
            vp.setMedij(p.medij());
            v.getPrilozi().add(vp);
        }
    }

    private void promeniStatus(Varijanta v, StatusVarijante status) {
        if (v.getStatus() != status) {
            v.setStatus(status);
            v.setIzmenjeno(sada());
        }
    }

    // ---------------------------------------------------------------- zaključavanje

    /** Red grupe pa red varijante (404 kad je nema); pre ključa samo id grupe. */
    private Varijanta zakljucaj(Long id) {
        return zakljucajAkoPostoji(id).orElseThrow(VarijantaService::nijePronadjena);
    }

    private Optional<Varijanta> zakljucajAkoPostoji(Long id) {
        Optional<Long> grupaId = varijantaRepository.findGrupaId(id);
        if (grupaId.isEmpty() || grupaRepository.findByIdForUpdate(grupaId.get()).isEmpty()) {
            return Optional.empty();
        }
        return varijantaRepository.findByIdForUpdate(id);
    }

    private GrupaZadataka zakljucajGrupu(Long grupaId) {
        return grupaRepository.findByIdForUpdate(grupaId).orElseThrow(VarijantaService::grupaNijePronadjena);
    }

    private TransactionTemplate transakcija() {
        TransactionTemplate tx = new TransactionTemplate(transakcije);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }

    // ---------------------------------------------------------------- DTO

    private static VarijantaInfo info(Varijanta v) {
        return new VarijantaInfo(v.getId(), v.getGrupa().getId(), v.getRb(), v.getNaziv(), v.getStatus(), v.getIzvor(),
                v.getRenderStatus(), id(v.getRenderSvg()), id(v.getRenderResenjeSvg()), v.getIzmenjeno());
    }

    private VarijantaDetails details(Varijanta v) {
        GrupaZadataka g = v.getGrupa();
        RenderVarijante.Greska greska = RenderVarijante.izKolone(v.getRenderGreska());
        List<PrilogInfo> prilozi = v.getPrilozi().stream()
                .sorted(Comparator.comparing(VarijantaPrilog::getIme))
                .map(p -> new PrilogInfo(p.getIme(), medijInfo(p.getMedij())))
                .toList();
        return new VarijantaDetails(v.getId(), g.getId(), grupaZadatakaService.putanja(g), v.getRb(), v.getNaziv(),
                v.getTekst(), v.getResenje(), PoljaJson.odgovoriIzKolone(v.getOdgovori()), PoljaJson.izKolone(g.getPolja()),
                prilozi, v.getStatus(), v.getIzvor(), v.getRenderStatus(), id(v.getRenderSvg()), id(v.getRenderResenjeSvg()),
                greska == null ? null : greska.info(), greska == null ? null : greska.deo(), v.getKreirano(), v.getIzmenjeno());
    }

    private static MedijInfo medijInfo(Medij m) {
        return new MedijInfo(m.getId(), m.getNaziv(), m.getMime(), m.getVelicina());
    }

    private static String id(Medij m) {
        return m == null ? null : m.getId();
    }

    private static String tekst(byte[] svg) {
        return svg == null ? null : new String(svg, StandardCharsets.UTF_8);
    }

    private static String prazanUNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock);
    }

    private static SystemException nijePronadjena() {
        return new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
    }

    private static SystemException grupaNijePronadjena() {
        return new SystemException(GrupaZadatakaService.NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
    }

    private static SystemException predmetNijePronadjen() {
        return new SystemException(GrupaZadatakaService.PREDMET_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
    }
}
