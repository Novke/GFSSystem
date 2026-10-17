package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
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

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
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
 * <b>Render pre ključa</b> (upis u tri koraka): (1) provera komande i kratka transakcija samo za čitanje (pravila grupe i
 * odgovora, preambula, sadržaj koji se renderuje), bez ključa; (2) render, van transakcije i bez ijednog ključa; (3) kratka
 * transakcija pod ključem: ponovo pravila grupe i odgovora, heš zaključanog sadržaja sa trenutnom preambulom, upis
 * {@code render_hash} onoga što je zaista renderovano, novi SVG mediji i brisanje starih. Ako se ulaz između (1) i (3)
 * promenio (preambula, istovremeni upis), izmena, duplikat i prisilan render renderuju ponovo pod ključem (retko), a
 * render cele banke rezultat odbacuje. Odluka "isti heš i OK, bez rendera" se donosi u (1) i ponovo proverava u (3).
 * <p>
 * <b>Zaključavanje</b> (ugovor iz {@link GrupaZadatakaService}): pre dodavanja ili izmene varijante red grupe
 * ({@code GrupaZadatakaRepository.findByIdForUpdate}), pa red varijante ({@code findByIdForUpdate}); drži najviše jednu
 * grupu, nikad ne zaključava predmet i nikad ne upisuje kolone {@code grupe_zadataka}. Kratke transakcije
 * koje servis sam otvara ({@code citaj} i {@code pisi}, kroz {@code TransactionTemplate}) počinju praznim persistence
 * context-om ({@code EntityManager.clear}): uz {@code open-in-view} isti EntityManager traje ceo HTTP zahtev, pa bi ključ
 * inače vratio entitet pročitan pre njega. Metode vođene anotacijom ({@code obrisi}, {@code odobri}, {@code arhiviraj},
 * {@code vratiUNacrt}) ne čiste context.
 * <p>
 * Izmene rade u READ COMMITTED, kao {@link GrupaZadatakaService}. Grupa bez težine ne prima varijante (409), kao što ni
 * {@code GrupaZadatakaService.izmeni} ne dozvoljava grupu sa varijantama bez težine.
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
    /** Render cele banke: greška koja nije SystemException (poruka izuzetka nije za nastavnika). */
    static final String RENDER_PAO = "Render nije uspeo.";

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
     * Nova varijanta na kraj grupe ({@code rb} = najveći + 1), NACRT, {@code izvor} iz komande ili RUCNO; render odmah,
     * pre ključa. Grupa mora biti list (409 za grupu sa podgrupama) sa težinom (409), odgovori moraju pokriti šemu polja
     * grupe; proverava se pre rendera i ponovo pod ključem.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VarijantaDetails kreiraj(Long grupaId, VarijantaCmd cmd) {
        Sadrzaj s = proveren(cmd);
        String preambula = citaj(() -> {
            GrupaZadataka g = grupaRepository.findById(grupaId).orElseThrow(VarijantaService::grupaNijePronadjena);
            proveriZaNovu(g, s);
            return g.getPredmet().getLatexPreambula();
        });
        Prerender pre = renderuj(new Ulaz(preambula, s.tekst(), s.resenje(), s.priloziCmd()));
        return pisi(() -> {
            GrupaZadataka g = zakljucajGrupu(grupaId);
            proveriZaNovu(g, s);
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
            Varijanta nova = varijantaRepository.save(v);   // id je deo naziva SVG medija
            primeniPosleKljuca(nova, pre, false);
            log.info("Varijanta kreirana: id={}, grupa={}, izvor={}, render={}", nova.getId(), grupaId, nova.getIzvor(),
                    nova.getRenderStatus());
            return details(nova);
        });
    }

    /**
     * Puna zamena naziva, teksta, rešenja, odgovora i priloga (izvor ostaje). Renderuje samo kad se ulaz rendera
     * promenio ili poslednji render nije uspeo; AKTIVNA ostaje AKTIVNA samo ako render prođe.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VarijantaDetails izmeni(Long id, VarijantaCmd cmd) {
        Sadrzaj s = proveren(cmd);
        Procitano procitano = citaj(() -> {
            Varijanta v = varijantaRepository.findById(id).orElseThrow(VarijantaService::nijePronadjena);
            proveriOdgovore(v.getGrupa(), s.odgovori());
            return new Procitano(v.getGrupa().getPredmet().getLatexPreambula(), v.getRenderHash(), v.getRenderStatus());
        });
        Ulaz ulaz = new Ulaz(procitano.preambula(), s.tekst(), s.resenje(), s.priloziCmd());
        boolean kesVazi = hash(ulaz).equals(procitano.renderHash()) && procitano.renderStatus() == RenderStatus.OK;
        Prerender pre = kesVazi ? null : renderuj(ulaz);
        return pisi(() -> {
            Varijanta v = zakljucaj(id);
            proveriOdgovore(v.getGrupa(), s.odgovori());
            primeni(v, s);
            v.setIzmenjeno(sada());
            boolean renderovano = primeniPosleKljuca(v, pre, false);
            log.info("Varijanta izmenjena: id={}, render={} ({})", id, v.getRenderStatus(), renderovano ? "nov" : "keš");
            return details(v);
        });
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

    /** Render bez obzira na keš (npr. posle pada servisa ili izmene paketa u kontejneru), pre ključa kao upis. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VarijantaDetails render(Long id) {
        Prerender pre = renderuj(citaj(() -> ulaz(varijantaRepository.findById(id).orElseThrow(VarijantaService::nijePronadjena))));
        return pisi(() -> {
            Varijanta v = zakljucaj(id);
            primeniPosleKljuca(v, pre, true);
            log.info("Varijanta renderovana: id={}, render={}", id, v.getRenderStatus());
            return details(v);
        });
    }

    /**
     * Kopija na kraj iste grupe: NACRT, isti naziv, sadržaj, odgovori, izvor i prilozi (isti mediji), sopstveni render
     * (SVG medij pripada jednoj varijanti, pa se ne deli). Kopira se zaključano stanje izvorne varijante.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VarijantaDetails dupliraj(Long id) {
        Prerender pre = renderuj(citaj(() -> ulaz(varijantaRepository.findById(id).orElseThrow(VarijantaService::nijePronadjena))));
        return pisi(() -> {
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
            Varijanta kopija = varijantaRepository.save(k);
            primeniPosleKljuca(kopija, pre, false);
            log.info("Varijanta duplirana: id={} -> {}, render={}", id, kopija.getId(), kopija.getRenderStatus());
            return details(kopija);
        });
    }

    // ---------------------------------------------------------------- render bez čuvanja i cela banka

    /**
     * Render bez čuvanja, sa preambulom predmeta, za pregled u editoru (i za Claude-a kroz MCP), bez transakcije. Ista
     * pravila teksta, rešenja i priloga kao upis; najviše {@value #MAX_PROBA} istovremeno (429 preko toga).
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
     * Ponovni render banke predmeta (posle izmene preambule): sinhrono, redom, bez ključa predmeta. Svaka varijanta se
     * čita i renderuje bez ključa, a upisuje u kratkoj transakciji pod ključem grupe pa varijante, samo ako je heš
     * zaključanog sadržaja sa trenutnom preambulom jednak renderovanom (inače se rezultat odbacuje i varijanta ostaje
     * zastarela). Prvo zastarele (heš sadržaja se razlikuje od poslednjeg rendera ili render čeka), pa one čiji je
     * poslednji render istog sadržaja pao; najviše {@value #MAX_RENDERA_PO_POZIVU} po pozivu i nijedna nova posle
     * {@link #MAX_TRAJANJE_RENDERA_SVIH}. Loša varijanta ne zaustavlja petlju: {@link SystemException} (npr. prilog bez fajla,
     * komanda koju je deny-lista u međuvremenu zabranila) i drugi {@link RuntimeException} se upisuju kao {@code GRESKA} sa
     * porukom izuzetka (ili opštom) i hešom njenog sadržaja (AKTIVNA pada u NACRT), pa se ide dalje; upis se preskače ako je
     * varijanta u međuvremenu već uspešno renderovana istim sadržajem. Greške okruženja prekidaju poziv bez ikakvog
     * označavanja: 503 servisa ({@link RenderNedostupan}), {@link DataAccessException}, {@link PersistenceException},
     * {@link TransactionException} i {@link UncheckedIOException} (npr. pun disk pri upisu SVG-a); već upisane ostaju. {@code renderovano} broji samo uspele ({@code OK}) rendere; {@code preostalo} samo zastarele koje nisu stigle
     * na red, pa front koji zove dok je {@code preostalo > 0} uvek stane.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RenderujSveInfo renderujSve(Long predmetId) {
        if (predmetId == null) {
            throw new SystemException(GrupaZadatakaService.PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        Kandidati kandidati = citaj(() -> kandidati(predmetId));
        Instant rok = clock.instant().plus(MAX_TRAJANJE_RENDERA_SVIH);
        int obradjeno = 0;
        int renderovano = 0;
        for (Long id : kandidati.redom()) {
            if (obradjeno == MAX_RENDERA_PO_POZIVU || !clock.instant().isBefore(rok)) {
                break;
            }
            obradjeno++;
            if (renderujJednu(id)) {
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
        predmetRepository.findById(predmetId).orElseThrow(VarijantaService::predmetNijePronadjen);
        List<Varijanta> sve = new ArrayList<>(varijantaRepository.svePredmeta(predmetId));
        sve.sort(Comparator.comparing(Varijanta::getId));
        List<Long> zastarele = new ArrayList<>();
        List<Long> pale = new ArrayList<>();
        for (Varijanta v : sve) {
            if (!hash(ulaz(v)).equals(v.getRenderHash()) || v.getRenderStatus() == RenderStatus.CEKA) {
                zastarele.add(v.getId());
            } else if (v.getRenderStatus() != RenderStatus.OK) {
                pale.add(v.getId());
            }
        }
        return new Kandidati(Stream.concat(zastarele.stream(), pale.stream()).toList(), zastarele.size());
    }

    /** Jedna varijanta cele banke; {@code true} samo kad je upisan uspeo render. */
    private boolean renderujJednu(Long id) {
        Ulaz ulaz = citaj(() -> varijantaRepository.findById(id).map(this::ulazAkoTreba).orElse(null));
        if (ulaz == null) {
            return false;   // nestala ili ju je neko u međuvremenu renderovao
        }
        String hash = hash(ulaz);
        try {
            RenderVarijante.Ishod ishod = renderVarijante.renderuj(ulaz.preambula(), ulaz.tekst(), ulaz.resenje(), ulaz.prilozi());
            return Boolean.TRUE.equals(pisi(() -> upisiAkoIsti(id, hash, ishod)));
        } catch (RuntimeException e) {
            if (jeInfrastrukturna(e)) {
                throw e;   // pun disk ili pad baze ne sme da spusti sve aktivne varijante u NACRT
            }
            oznaciGresku(id, hash, e);
            return false;
        }
    }

    /**
     * Greška okruženja, ne varijante: render servis ({@link RenderNedostupan}), baza ({@link DataAccessException},
     * {@link PersistenceException}, {@link TransactionException}) ili disk pri upisu SVG-a ({@link UncheckedIOException}).
     * Prekida render cele banke; varijanta se zbog nje ne označava kao {@code GRESKA}.
     */
    private static boolean jeInfrastrukturna(RuntimeException e) {
        return e instanceof RenderNedostupan || e instanceof DataAccessException || e instanceof PersistenceException
                || e instanceof TransactionException || e instanceof UncheckedIOException;
    }

    private Ulaz ulazAkoTreba(Varijanta v) {
        Ulaz ulaz = ulaz(v);
        return hash(ulaz).equals(v.getRenderHash()) && v.getRenderStatus() == RenderStatus.OK ? null : ulaz;
    }

    /** Pod ključem: upis rendera samo za isti ulaz i kad ga u međuvremenu niko drugi nije osvežio. */
    private boolean upisiAkoIsti(Long id, String hash, RenderVarijante.Ishod ishod) {
        Optional<Varijanta> zakljucana = zakljucajAkoPostoji(id);
        if (zakljucana.isEmpty()) {
            return false;
        }
        Varijanta v = zakljucana.get();
        String sada = hash(ulaz(v));
        if (!sada.equals(hash) || (sada.equals(v.getRenderHash()) && v.getRenderStatus() == RenderStatus.OK)) {
            return false;
        }
        primeniRender(v, hash, ishod);
        return ishod.ok();
    }

    /** Greška pre ili posle rendera jedne varijante cele banke: upisuje se kao GRESKA (bez dela), u novoj transakciji. */
    private void oznaciGresku(Long id, String hash, RuntimeException uzrok) {
        String poruka = uzrok instanceof SystemException && uzrok.getMessage() != null ? uzrok.getMessage() : RENDER_PAO;
        if (uzrok instanceof SystemException) {
            log.warn("Render banke: varijanta {} nije renderovana: {}", id, poruka);
        } else {
            log.warn("Render banke: varijanta {} nije renderovana", id, uzrok);
        }
        RenderVarijante.Ishod greska = new RenderVarijante.Ishod(false, null, null, new RenderGreskaInfo(null, poruka), null);
        try {
            pisi(() -> {
                zakljucajAkoPostoji(id)
                        .filter(v -> hash(ulaz(v)).equals(hash))
                        .filter(v -> !(hash.equals(v.getRenderHash()) && v.getRenderStatus() == RenderStatus.OK))   // neko ju je u međuvremenu uspešno renderovao
                        .ifPresent(v -> primeniRender(v, hash, greska));
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Render banke: greška varijante {} nije upisana", id, e);
        }
    }

    // ---------------------------------------------------------------- render i SVG mediji

    /** Ulaz rendera: preambula predmeta, tekst, rešenje i prilozi (po imenu kao u heš-u). */
    private record Ulaz(String preambula, String tekst, String resenje, List<PrilogCmd> prilozi) {
    }

    /** Render urađen pre ključa: heš ulaza i ishod. */
    private record Prerender(String hash, RenderVarijante.Ishod ishod) {
    }

    /** Stanje varijante pročitano pre ključa za odluku o kešu izmene. */
    private record Procitano(String preambula, String renderHash, RenderStatus renderStatus) {
    }

    private static Ulaz ulaz(Varijanta v) {
        return new Ulaz(v.getGrupa().getPredmet().getLatexPreambula(), v.getTekst(), v.getResenje(), prilozi(v));
    }

    private String hash(Ulaz u) {
        return renderVarijante.hash(u.preambula(), u.tekst(), u.resenje(), u.prilozi());
    }

    /** Render van transakcije i bez ključa. */
    private Prerender renderuj(Ulaz u) {
        return new Prerender(hash(u), renderVarijante.renderuj(u.preambula(), u.tekst(), u.resenje(), u.prilozi()));
    }

    /**
     * Pod ključem: heš zaključanog sadržaja sa trenutnom preambulom. Isti heš i {@code OK} (bez {@code prisilno}) =
     * ništa; render pre ključa za isti heš se upisuje; inače (preambula ili sadržaj promenjeni posle čitanja, retko) render
     * pod ključem. Vraća da li je render upisan.
     */
    private boolean primeniPosleKljuca(Varijanta v, Prerender pre, boolean prisilno) {
        Ulaz ulaz = ulaz(v);
        String hash = hash(ulaz);
        if (!prisilno && hash.equals(v.getRenderHash()) && v.getRenderStatus() == RenderStatus.OK) {
            return false;
        }
        RenderVarijante.Ishod ishod;
        if (pre != null && hash.equals(pre.hash())) {
            ishod = pre.ishod();
        } else {
            log.info("Varijanta {}: ulaz rendera se promenio posle čitanja, render pod ključem", v.getId());
            ishod = renderVarijante.renderuj(ulaz.preambula(), ulaz.tekst(), ulaz.resenje(), ulaz.prilozi());
        }
        primeniRender(v, hash, ishod);
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

    /** Proverena komanda, pre svake transakcije; odgovori naspram šeme grupe se proveravaju u transakcijama. */
    private record Sadrzaj(String naziv, String tekst, String resenje, Map<String, OdgovorPolja> odgovori, String odgovoriJson,
                           List<Prilog> prilozi) {
        List<PrilogCmd> priloziCmd() {
            return prilozi.stream().map(p -> new PrilogCmd(p.ime(), p.medij().getId())).toList();
        }
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

    /** Nova varijanta: grupa je list (409) sa težinom (409), odgovori po šemi grupe (400). */
    private void proveriZaNovu(GrupaZadataka g, Sadrzaj s) {
        if (grupaRepository.countByRoditeljId(g.getId()) > 0) {
            throw new SystemException(IMA_PODGRUPE, HttpStatus.CONFLICT);
        }
        if (g.getTezina() == null) {
            throw new SystemException(GrupaZadatakaService.TEZINA_OBAVEZNA, HttpStatus.CONFLICT);
        }
        proveriOdgovore(g, s.odgovori());
    }

    /** Odgovori naspram šeme polja grupe. Grupa bez polja prima samo prazne odgovore. */
    private static void proveriOdgovore(GrupaZadataka g, Map<String, OdgovorPolja> odgovori) {
        BankaPP.proveriOdgovore(BankaPP.normalizujPolja(PoljaJson.izKolone(g.getPolja())), odgovori);
    }

    /** Sadržaj komande u varijantu; mediji priloga (pročitani pre transakcije) kao reference u tekućem kontekstu. */
    private void primeni(Varijanta v, Sadrzaj s) {
        v.setNaziv(s.naziv());
        v.setTekst(s.tekst());
        v.setResenje(s.resenje());
        v.setOdgovori(s.odgovoriJson());
        primeniPriloge(v, s.prilozi().stream()
                .map(p -> new Prilog(p.ime(), medijRepository.getReferenceById(p.medij().getId()))).toList());
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

    /** Red grupe pa red varijante (404 kad je nema); pre ključa samo id grupe (skalar). */
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

    /** Kratka transakcija samo za čitanje, bez ključeva. */
    private <T> T citaj(Supplier<T> posao) {
        return transakcija(true).execute(status -> {
            em.clear();
            return posao.get();
        });
    }

    /** Kratka transakcija za upis (ključevi grupa pa varijanta). */
    private <T> T pisi(Supplier<T> posao) {
        return transakcija(false).execute(status -> {
            em.clear();   // open-in-view: bez ovoga bi ključ vratio entitet pročitan ranije u istom zahtevu
            return posao.get();
        });
    }

    private TransactionTemplate transakcija(boolean samoCitanje) {
        TransactionTemplate tx = new TransactionTemplate(transakcije);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setReadOnly(samoCitanje);
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
