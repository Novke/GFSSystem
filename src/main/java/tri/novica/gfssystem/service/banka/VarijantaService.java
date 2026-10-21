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
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;
import tri.novica.gfssystem.repository.uzivo.ZadatakDodelaRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.utility.Serija;
import tri.novica.gfssystem.validation.BankaPP;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Varijante zadataka banke (spec 2.3, 2.4): CRUD, statusi, render sa kešom po hešu, prilozi i render-proba.
 * <p>
 * <b>Render:</b> pri svakom upisu (kreiranje, izmena, duplikat) servis računa heš ulaza rendera
 * ({@link RenderVarijante#hash}); renderuje sinhrono u istom zahtevu, osim kad je heš jednak sopstvenom prethodnom
 * ({@code render_hash} te varijante, nikad tuđem) i render je {@code OK}. Uspeh upisuje nove SVG medije ({@code RENDER_SVG},
 * {@code zadatak-<id>.svg}, {@code resenje-<id>.svg}) pa tek onda briše stare (osim kad je varijanta ikad bila
 * odobrena, vidi dole); neuspeh je stanje, ne izuzetak ({@code GRESKA}, greška u {@code render_greska} kao
 * {@code <deo>:<linija>: <poruka>}, stari SVG ostaje), a AKTIVNA tada pada u NACRT pri čuvanju jedne varijante
 * (kreiranje, izmena, prisilan render, duplikat). Render cele banke ({@link #renderujSve}, posle izmene preambule) ne
 * spušta status: aktivna sa neuspelim renderom ostaje aktivna i prikazuje poslednji uspeo SVG.
 * Render servis koji ne radi ({@link RenderNedostupan}, 503) poništava ceo zahtev: ništa se ne upisuje (ni nova varijanta ni
 * izmena), front zadržava unos i ponavlja. {@link MedijService} briše fajl starog SVG-a tek posle commit-a, a fajl novog
 * briše ako se transakcija poništi: pad commit-a ostavlja varijantu sa starim SVG-om i njegovim fajlom, bez novih siročadi.
 * <p>
 * <b>SVG odobrene varijante se nikad ne briše:</b> snimak runde Zadatak slajda čuva samo id-jeve SVG-ova
 * ({@code svgId}, {@code resenjeSvgId}), a izvlače se samo AKTIVNE, pa svaka varijanta iz snimka ima {@code ikadOdobrena}
 * (ostaje i posle vraćanja u nacrt i arhiviranja). Za takvu varijantu render (izmena, prisilan render, render cele banke)
 * upisuje nove SVG medije, a stare ne briše, i brisanje varijante (ili njene grupe) briše red, a ne i SVG medije; tako
 * slike ranijih rundi u pregledu časa ostaju. Brisanje varijante koja je u snimku neke runde ili ima dodelu odbija
 * {@link #koriscenoNaCasu} (409), pa SVG ostaje pre svega zbog izmene (stari SVG ostaje u snimku) i kao mreža iza te
 * provere. Siročad {@code RENDER_SVG} su prihvaćena (mali SVG-ovi). Varijanta koja nikad nije odobrena (nacrt, npr.
 * Klodov) briše stare kao pre.
 * <p>
 * <b>Render pre ključa</b> (upis u tri koraka): (1) provera komande i kratka transakcija samo za čitanje (pravila grupe i
 * odgovora, preambula, sadržaj koji se renderuje), bez ključa; (2) render, van transakcije i bez ijednog ključa; (3) kratka
 * transakcija pod ključem: ponovo pravila grupe i odgovora, heš zaključanog sadržaja sa trenutnom preambulom, upis
 * {@code render_hash} onoga što je zaista renderovano, novi SVG mediji i brisanje starih. Ako se ulaz između (1) i (3)
 * promenio (preambula, istovremeni upis), izmena, duplikat i prisilan render renderuju ponovo pod ključem (retko), a
 * render cele banke rezultat odbacuje. Odluka "isti heš i OK, bez rendera" se donosi u (1) i ponovo proverava u (3).
 * <p>
 * <b>Klodovi upisi</b> (MCP, spec MCP 4.1): {@link #kreiraj(Long, VarijantaCmd, String, boolean)}, {@link #izmeniDelimicno}
 * i {@link #dupliraj(Long, String)} upisuju seriju, a svaki red koji naprave (nova varijanta, duplikat, kopija iz izmene) dobija
 * izvor CLAUDE od samog servisa, šta god nosi komanda; prva dva vraćaju i PNG iz istog prevođenja. Na mestu se menja i briše
 * ({@link #obrisiSopstveniNacrt}) samo sopstveni neodobren nacrt (NACRT, CLAUDE, nikad odobren); izmena svake druge varijante
 * je kopija-nacrt koja zamenjuje original. Njihov korak (3) <b>nikad ne renderuje pod ključem</b>: ulaz promenjen posle
 * čitanja je 409 {@value #IZMENJENA_U_MEDJUVREMENU} i ništa se ne upisuje (ponavljanje je bezbedno).
 * {@link #slika} i {@link #probaSaSlikom} renderuju samo PNG, bez upisa, kroz mesta za probe.
 * <p>
 * <b>Zaključavanje</b> (ugovor iz {@link GrupaZadatakaService}): pre dodavanja ili izmene varijante red grupe
 * ({@code GrupaZadatakaRepository.findByIdForUpdate}), pa red varijante ({@code findByIdForUpdate}); drži najviše jednu
 * grupu, nikad ne zaključava predmet i nikad ne upisuje kolone {@code grupe_zadataka}. Kratke transakcije
 * koje servis sam otvara ({@code citaj} i {@code pisi}, kroz {@code TransactionTemplate}) počinju praznim persistence
 * context-om ({@code EntityManager.clear}): uz {@code open-in-view} isti EntityManager traje ceo HTTP zahtev, pa bi ključ
 * inače vratio entitet pročitan pre njega. Metode vođene anotacijom ({@code obrisi}, {@code obrisiSopstveniNacrt},
 * {@code odobri}, {@code arhiviraj}, {@code vratiUNacrt}) ne čiste context.
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
    static final String NIJE_PRILOG = "Medij nije prilog zadatka.";
    static final String PRILOZI_PREVELIKI = "Prilozi su preveliki za render (ukupno najviše oko 30 MB fajlova).";
    static final String RENDER_SVIH_U_TOKU = "Render cele banke za ovaj predmet je već u toku.";
    /** Render cele banke: greška koja nije SystemException (poruka izuzetka nije za nastavnika). */
    static final String RENDER_PAO = "Render nije uspeo.";
    /** MCP upis čiji bi korak 3 morao da renderuje pod ključem (sadržaj ili preambula promenjeni tokom rendera). */
    static final String IZMENJENA_U_MEDJUVREMENU = "Varijanta ili preambula je u međuvremenu izmenjena; pokušaj ponovo.";
    static final String SAMO_SOPSTVENI_NACRT = "Klod briše samo svoje neodobrene nacrte.";

    /** Kontejner prevodi najviše 4 dokumenta odjednom: probe dobijaju 3, jedno mesto ostaje za čuvanje varijante. */
    static final int MAX_PROBA = 3;
    /** Render cele banke: najviše ovoliko varijanti po pozivu ... */
    static final int MAX_RENDERA_PO_POZIVU = 200;
    /** ... i ne počinje novu posle ovoliko vremena, da odgovor stigne pre isteka proksija (front nginx čeka 120 s). */
    static final Duration MAX_TRAJANJE_RENDERA_SVIH = Duration.ofSeconds(40);
    private static final int MAX_PRILOGA = 10;
    /**
     * Prilozi jednog rendera kao base64 (4/3 veličine, oko 30 MB fajlova) plus tekst i rešenje: render servis prima
     * najviše 60 MB tela ({@code RENDER_MAX_BODY}), ovo ostavlja rezervu.
     */
    private static final long MAX_PRILOZI_ZA_RENDER_BAJTOVA = 40L * 1024 * 1024;
    /** Kolona {@code odgovori} je MySQL {@code text}: najviše 65 535 bajtova. */
    private static final int MAX_ODGOVORI_BAJTOVA = 65_535;
    /** Render servis piše {@code dokument.tex}, {@code .pdf} i {@code .log}: prilog tog imena bi ih pregazio. */
    private static final String REZERVISANO = "dokument";

    private final GrupaZadatakaRepository grupaRepository;
    private final VarijantaRepository varijantaRepository;
    private final PredmetRepository predmetRepository;
    private final MedijRepository medijRepository;
    private final ZadatakDodelaRepository dodelaRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final MedijService medijService;
    private final RenderVarijante renderVarijante;
    private final GrupaZadatakaService grupaZadatakaService;
    private final PlatformTransactionManager transakcije;
    private final EntityManager em;
    private final Clock clock;
    /** Render-proba: najviše {@value #MAX_PROBA} istovremeno po backendu, preko toga 429 (ne čeka se). */
    private final Semaphore probe = new Semaphore(MAX_PROBA);
    /** Predmeti čiji render cele banke upravo radi (jedan backend): drugi poziv za isti predmet je 409. */
    private final Set<Long> renderSvihUToku = ConcurrentHashMap.newKeySet();

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

    /**
     * Da li je varijanta ikad izvučena na času: ima dodelu (red u {@code zadatak_dodele}) ili je u snimku neke runde
     * (izvučena, a nijednom učesniku nije dodeljena: manje učesnika od varijanti, ili učesnik koji tek dolazi). Takva se ne
     * briše, nego arhivira (banka spec 4.1). FK RESTRICT štiti samo dodeljenu, izvučenu štiti snimak. Provera vidi samo
     * commit-ovane runde: otvaranje runde (komanda Zadatak slajda) zaključava kandidate pre izvlačenja
     * ({@code VarijantaRepository.zakljucajKandidate}, posle zaključanog izvođenja, pre upisa snimka), pa brisanje (koje
     * zaključava varijantu pre ove provere) ili čeka otvaranje i vidi njegov snimak, ili ga otvaranje čeka i ne izvlači
     * obrisanu varijantu.
     */
    public boolean koriscenoNaCasu(Long varijantaId) {
        return dodelaRepository.existsByVarijantaId(varijantaId) || rundaRepository.varijantaUSnimku(varijantaId);
    }

    // ---------------------------------------------------------------- upis

    /**
     * Nova varijanta na kraj grupe ({@code rb} = najveći + 1), NACRT, {@code izvor} iz komande ili RUCNO; render odmah,
     * pre ključa. Grupa mora biti list (409 za grupu sa podgrupama) sa težinom (409), odgovori moraju pokriti šemu polja
     * grupe; proverava se pre rendera i ponovo pod ključem.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VarijantaDetails kreiraj(Long grupaId, VarijantaCmd cmd) {
        IzvorVarijante izvor = cmd == null || cmd.izvor() == null ? IzvorVarijante.RUCNO : cmd.izvor();
        return novaVarijanta(grupaId, cmd, izvor, null, false, false).varijanta();
    }

    /**
     * Klodova nova varijanta (MCP {@code napravi_varijantu}): kao {@link #kreiraj(Long, VarijantaCmd)}, ali je izvor uvek
     * CLAUDE ({@code cmd.izvor()} se ne čita: MCP nikad ne pravi RUCNO nacrt sa serijom), uz seriju ({@code null} = bez,
     * van {@link Serija#OBRAZAC} 400) i PNG zadatka i rešenja iz istog prevođenja kad je {@code png}. Korak 3 ne renderuje
     * pod ključem: preambula promenjena tokom rendera je 409 {@value #IZMENJENA_U_MEDJUVREMENU} i nema novog reda.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UpisVarijante kreiraj(Long grupaId, VarijantaCmd cmd, String serija, boolean png) {
        if (serija != null) {
            Serija.proveri(serija);
        }
        return novaVarijanta(grupaId, cmd, IzvorVarijante.CLAUDE, serija, png, true);
    }

    private UpisVarijante novaVarijanta(Long grupaId, VarijantaCmd cmd, IzvorVarijante izvor, String serija, boolean png,
                                        boolean bezRenderaPodKljucem) {
        Sadrzaj s = proveren(cmd);
        String preambula = citaj(() -> {
            GrupaZadataka g = grupaRepository.findById(grupaId).orElseThrow(VarijantaService::grupaNijePronadjena);
            proveriZaNovu(g, s);
            return g.getPredmet().getLatexPreambula();
        });
        Prerender pre = renderuj(new Ulaz(preambula, s.tekst(), s.resenje(), s.priloziCmd()), png);
        return pisi(() -> {
            GrupaZadataka g = zakljucajGrupu(grupaId);
            proveriZaNovu(g, s);
            Varijanta v = novaUGrupi(g, izvor, serija);
            primeni(v, s);
            Varijanta nova = varijantaRepository.save(v);   // id je deo naziva SVG medija
            RenderVarijante.Ishod ishod = primeniPosleKljuca(nova, pre, false, bezRenderaPodKljucem);
            log.info("Varijanta kreirana: id={}, grupa={}, izvor={}, serija={}, render={}", nova.getId(), grupaId,
                    nova.getIzvor(), serija, nova.getRenderStatus());
            return new UpisVarijante(details(nova), UpisVarijante.Radnja.NOVA, slike(png, ishod));
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
            boolean renderovano = primeniPosleKljuca(v, pre, false, false) != null;
            log.info("Varijanta izmenjena: id={}, render={} ({})", id, v.getRenderStatus(), renderovano ? "nov" : "keš");
            return details(v);
        });
    }

    /**
     * Klodova delimična izmena (MCP {@code izmeni_varijantu}, spec MCP 4.1): patch se spaja nad trenutnim stanjem, a spojen
     * sadržaj prolazi ista pravila kao {@link #izmeni}. Na mestu se menja samo sopstveni neodobren nacrt (NACRT, CLAUDE,
     * nikad odobren; serija ostaje ona koja ga je napravila). Svaka druga varijanta dobija kopiju-nacrt na kraju iste grupe
     * (CLAUDE, {@code serija}, {@code zamenjuje} = original) sa primenjenom izmenom, a original se ne dira; kad Novica
     * odobri kopiju, {@link #odobri} arhivira original. Tri koraka kao {@code izmeni}: (1) spajanje i odluka nad pročitanim
     * stanjem, (2) render (keš samo za izmenu na mestu), (3) pod ključem ponovo spajanje i odluka nad zaključanim stanjem
     * (odobren u međuvremenu = kopija). Korak 3 nikad ne renderuje pod ključem: upisuje render iz koraka 2 kad mu je heš
     * jednak hešu spojenog sadržaja sa trenutnom preambulom, ili ostavlja postojeći render sopstvenog nacrta istog heša;
     * inače (Novica je u međuvremenu promenio sadržaj ili preambulu, ili je odluka postala kopija a korak 2 nije renderovao
     * jer je keš važio) 409 {@value #IZMENJENA_U_MEDJUVREMENU} i ništa se ne upisuje.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UpisVarijante izmeniDelimicno(Long id, VarijantaPatch patch, String serija, boolean png) {
        if (patch == null) {
            throw new SystemException(GrupaZadatakaService.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        if (serija != null) {
            Serija.proveri(serija);
        }
        Spojeno spojeno = citaj(() -> {
            Varijanta v = varijantaRepository.findById(id).orElseThrow(VarijantaService::nijePronadjena);
            Sadrzaj s = proveren(spoji(v, patch));
            proveriOdgovore(v.getGrupa(), s.odgovori());
            return new Spojeno(s, v.getGrupa().getPredmet().getLatexPreambula(), sopstveniNacrt(v), v.getRenderHash(),
                    v.getRenderStatus());
        });
        Sadrzaj procitan = spojeno.sadrzaj();
        Ulaz ulaz = new Ulaz(spojeno.preambula(), procitan.tekst(), procitan.resenje(), procitan.priloziCmd());
        boolean kesVazi = spojeno.naMestu() && hash(ulaz).equals(spojeno.renderHash())
                && spojeno.renderStatus() == RenderStatus.OK;
        Prerender pre = kesVazi ? null : renderuj(ulaz, png);
        return pisi(() -> {
            Varijanta v = zakljucaj(id);
            Sadrzaj s = proveren(spoji(v, patch));
            if (sopstveniNacrt(v)) {   // ponovo, nad zaključanim stanjem: Novica je u međuvremenu mogao da odobri
                proveriOdgovore(v.getGrupa(), s.odgovori());
                primeni(v, s);
                v.setIzmenjeno(sada());
                RenderVarijante.Ishod ishod = primeniPosleKljuca(v, pre, false, true);
                log.info("Varijanta izmenjena (Klod): id={}, serija={}, render={} ({})", id, serija, v.getRenderStatus(),
                        ishod == null ? "keš" : "nov");
                return new UpisVarijante(details(v), UpisVarijante.Radnja.IZMENJENA, slike(png, ishod));
            }
            GrupaZadataka g = v.getGrupa();
            proveriZaNovu(g, s);
            Varijanta k = novaUGrupi(g, IzvorVarijante.CLAUDE, serija);
            k.setZamenjuje(v);
            primeni(k, s);
            Varijanta kopija = varijantaRepository.save(k);
            RenderVarijante.Ishod ishod = primeniPosleKljuca(kopija, pre, false, true);
            log.info("Varijanta {} izmenjena kao kopija (Klod): id={}, serija={}, render={}", id, kopija.getId(), serija,
                    kopija.getRenderStatus());
            return new UpisVarijante(details(kopija), UpisVarijante.Radnja.KOPIJA, slike(png, ishod));
        });
    }

    /**
     * Briše varijantu (prilozi kaskadom; mediji priloga ostaju, duplikat ih može deliti), pa njene SVG medije, osim kad je
     * ikad bila odobrena (mogla je ući u snimak runde, vidi <b>Render</b>); korišćena na času je 409.
     */
    public void obrisi(Long id) {
        obrisiZakljucanu(zakljucaj(id));
    }

    /**
     * Klodovo brisanje (MCP {@code obrisi_varijantu}): pod ključem, samo sopstveni neodobren nacrt (NACRT, CLAUDE, nikad
     * odobren), inače 409 {@value #SAMO_SOPSTVENI_NACRT}; dalje kao {@link #obrisi}.
     */
    public void obrisiSopstveniNacrt(Long id) {
        Varijanta v = zakljucaj(id);
        if (!sopstveniNacrt(v)) {
            throw new SystemException(SAMO_SOPSTVENI_NACRT, HttpStatus.CONFLICT);
        }
        obrisiZakljucanu(v);
    }

    private void obrisiZakljucanu(Varijanta v) {
        Long id = v.getId();
        if (koriscenoNaCasu(id)) {
            throw new SystemException(GrupaZadatakaService.KORISCENO_NA_CASU, HttpStatus.CONFLICT);
        }
        List<String> svg = v.isIkadOdobrena() ? List.of() : svgIds(v);
        varijantaRepository.delete(v);
        varijantaRepository.flush();
        svg.forEach(medijService::obrisi);
        log.info("Varijanta obrisana: id={}", id);
    }

    /**
     * U AKTIVNU (i iz ARHIVIRANE): render mora biti uspeo (409) i odgovori potpuni po trenutnoj šemi grupe (400, poruke
     * {@code BankaPP.proveriOdgovore}). Već aktivna ostaje kakva je. Odobrena varijanta pamti da je bila odobrena
     * ({@code ikadOdobrena}, vraćanje u nacrt to ne briše). Kopija koja zamenjuje original ({@code zamenjuje}, Klodova izmena
     * tuđe ili odobrene varijante) pri prelasku u AKTIVNU u istoj transakciji arhivira original, ako on još postoji i nije
     * arhiviran, pa se aktivni skup grupe ne menja do odobrenja. Original je u istoj grupi (kopija se pravi u grupi
     * originala, a varijanta ne menja grupu), čiji red je već zaključan; zaključava se posle kopije. Ponovno odobrenje već
     * aktivne kopije ne dira original (Novica ga je možda posle vratio).
     */
    public VarijantaDetails odobri(Long id) {
        Varijanta v = zakljucaj(id);
        if (v.getRenderStatus() != RenderStatus.OK) {
            throw new SystemException(RENDER_NIJE_USPEO, HttpStatus.CONFLICT);
        }
        proveriOdgovore(v.getGrupa(), PoljaJson.odgovoriIzKolone(v.getOdgovori()));
        boolean prelazi = v.getStatus() != StatusVarijante.AKTIVNA;
        promeniStatus(v, StatusVarijante.AKTIVNA);
        v.setIkadOdobrena(true);
        Long originalId = zamenjujeId(v);
        if (prelazi && originalId != null && !originalId.equals(id)) {
            varijantaRepository.findByIdForUpdate(originalId)
                    .filter(o -> o.getStatus() != StatusVarijante.ARHIVIRANA)
                    .ifPresent(o -> {
                        promeniStatus(o, StatusVarijante.ARHIVIRANA);
                        log.info("Varijanta {} odobrena umesto {}: original arhiviran", id, originalId);
                    });
        }
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
            primeniPosleKljuca(v, pre, true, false);
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
        return duplikat(id, null, null, false);
    }

    /**
     * Klodov duplikat (MCP {@code dupliraj_varijantu}): kao {@link #dupliraj(Long)}, ali je kopija uvek CLAUDE (i kad je
     * original Novicin), sa serijom ({@code null} = bez, van {@link Serija#OBRAZAC} 400). Korak 3 ne renderuje pod
     * ključem: original ili preambula promenjeni tokom rendera su 409 {@value #IZMENJENA_U_MEDJUVREMENU} i nema novog reda.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VarijantaDetails dupliraj(Long id, String serija) {
        if (serija != null) {
            Serija.proveri(serija);
        }
        return duplikat(id, IzvorVarijante.CLAUDE, serija, true);
    }

    /** {@code izvor} kopije; {@code null} = izvor originala (samo REST {@link #dupliraj(Long)}). */
    private VarijantaDetails duplikat(Long id, IzvorVarijante izvor, String serija, boolean bezRenderaPodKljucem) {
        Prerender pre = renderuj(citaj(() -> ulaz(varijantaRepository.findById(id).orElseThrow(VarijantaService::nijePronadjena))));
        return pisi(() -> {
            Varijanta original = zakljucaj(id);
            Varijanta k = novaUGrupi(original.getGrupa(), izvor == null ? original.getIzvor() : izvor, serija);
            k.setNaziv(original.getNaziv());
            k.setTekst(original.getTekst());
            k.setResenje(original.getResenje());
            k.setOdgovori(original.getOdgovori());
            primeniPriloge(k, original.getPrilozi().stream().map(p -> new Prilog(p.getIme(), p.getMedij())).toList());
            Varijanta kopija = varijantaRepository.save(k);
            primeniPosleKljuca(kopija, pre, false, bezRenderaPodKljucem);
            log.info("Varijanta duplirana: id={} -> {}, izvor={}, serija={}, render={}", id, kopija.getId(), kopija.getIzvor(),
                    serija, kopija.getRenderStatus());
            return details(kopija);
        });
    }

    // ---------------------------------------------------------------- render bez čuvanja i cela banka

    /**
     * Render bez čuvanja, sa preambulom predmeta, za pregled u editoru (Klod kroz MCP: {@link #probaSaSlikom}), bez transakcije. Ista
     * pravila teksta, rešenja i priloga kao upis; najviše {@value #MAX_PROBA} istovremeno (429 preko toga). Kad padne samo
     * rešenje, odgovor nosi i SVG zadatka ({@code ok=false}, {@code deo=resenje}).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RenderProbaInfo proba(RenderProbaCmd cmd) {
        RenderVarijante.Ishod ishod = probniRender(cmd, false);
        return new RenderProbaInfo(ishod.ok(), tekst(ishod.svg()), tekst(ishod.resenjeSvg()), ishod.greska(), ishod.deo());
    }

    /**
     * Render-proba za Kloda (MCP {@code proba_rendera}): ista pravila i ista mesta za probe kao {@link #proba}, ali samo u PNG
     * (ishod bez SVG-a); kad padne samo rešenje, nosi PNG zadatka. Ništa se ne upisuje.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProbaSaSlikom probaSaSlikom(RenderProbaCmd cmd) {
        RenderVarijante.Ishod ishod = probniRender(cmd, true);
        return new ProbaSaSlikom(new RenderProbaInfo(ishod.ok(), null, null, ishod.greska(), ishod.deo()),
                new SlikeRendera(ishod.png(), ishod.resenjePng()));
    }

    /**
     * PNG sačuvanog sadržaja varijante za Kloda (MCP {@code varijanta} sa slikom): render samo u PNG (zadatak i, kad
     * postoji, rešenje) kroz mesta za probe (najviše {@value #MAX_PROBA} istovremeno, preko toga 429); ništa se ne upisuje
     * (ni render, ni heš, ni status). {@code zadatak} je {@code null} kad se tekst ne prevodi, {@code resenje} kad rešenja
     * nema ili se ne prevodi.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SlikeRendera slika(Long id) {
        Ulaz u = citaj(() -> ulaz(varijantaRepository.findById(id).orElseThrow(VarijantaService::nijePronadjena)));
        RenderVarijante.Ishod ishod = naMestuProbe(() ->
                renderVarijante.renderuj(u.preambula(), u.tekst(), u.resenje(), u.prilozi(), false, true));
        return new SlikeRendera(ishod.png(), ishod.resenjePng());
    }

    /** Provera probe kao pri upisu, pa render sa preambulom predmeta na mestu za probe: SVG ili samo PNG. */
    private RenderVarijante.Ishod probniRender(RenderProbaCmd cmd, boolean png) {
        if (cmd == null) {
            throw new SystemException(GrupaZadatakaService.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        if (cmd.predmetId() == null) {
            throw new SystemException(GrupaZadatakaService.PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        String resenje = prazanUNull(cmd.resenje());
        BankaPP.proveriVarijantu(cmd.tekst(), resenje, null);
        RenderVarijante.proveriTelo(cmd.tekst(), resenje);
        List<PrilogCmd> prilozi = proveriPriloge(cmd.prilozi(), cmd.tekst(), resenje).stream()
                .map(p -> new PrilogCmd(p.ime(), p.medij().getId())).toList();
        Predmet predmet = predmetRepository.findById(cmd.predmetId()).orElseThrow(VarijantaService::predmetNijePronadjen);
        return naMestuProbe(() -> png
                ? renderVarijante.renderuj(predmet.getLatexPreambula(), cmd.tekst(), resenje, prilozi, false, true)
                : renderVarijante.renderuj(predmet.getLatexPreambula(), cmd.tekst(), resenje, prilozi));
    }

    /** Render na jednom od {@value #MAX_PROBA} mesta za probe; bez slobodnog mesta 429 (ne čeka se). */
    private <T> T naMestuProbe(Supplier<T> posao) {
        if (!probe.tryAcquire()) {
            throw new SystemException(PREVISE_PROBA, HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            return posao.get();
        } finally {
            probe.release();
        }
    }

    /**
     * Ponovni render banke predmeta (posle izmene preambule): sinhrono, redom, bez ključa predmeta. Svaka varijanta se
     * čita i renderuje bez ključa, a upisuje u kratkoj transakciji pod ključem grupe pa varijante, samo ako je heš
     * zaključanog sadržaja sa trenutnom preambulom jednak renderovanom (inače se rezultat odbacuje i varijanta ostaje
     * zastarela). Kandidati su zastarele (heš sadržaja se razlikuje od poslednjeg rendera ili render čeka), a samo kad
     * nijedne nema one čiji je poslednji render istog sadržaja pao: tok posle izmene preambule tako nijednu palu ne
     * renderuje dvaput (i broji je jednom), a poziv bez zastarelih ponavlja pale; najviše {@value #MAX_RENDERA_PO_POZIVU} po pozivu i nijedna nova posle
     * {@link #MAX_TRAJANJE_RENDERA_SVIH}. Neuspeo render <b>ne spušta status</b>: upisuje se samo {@code GRESKA} i poruka, a
     * varijanta (i AKTIVNA) zadržava status i poslednji uspeo SVG, jer jedna greška u preambuli obara svaku varijantu. Loša
     * varijanta ne zaustavlja petlju: {@link SystemException} (npr. prilog bez fajla, komanda koju je deny-lista u
     * međuvremenu zabranila) i drugi {@link RuntimeException} se upisuju kao {@code GRESKA} sa porukom izuzetka (ili opštom) i
     * hešom njenog sadržaja, pa se ide dalje; upis se preskače ako je varijanta u međuvremenu već uspešno renderovana istim
     * sadržajem. Greške okruženja prekidaju poziv bez ikakvog označavanja: 503 servisa ({@link RenderNedostupan}),
     * {@link DataAccessException}, {@link PersistenceException}, {@link TransactionException} i {@link UncheckedIOException}
     * (npr. pun disk pri upisu SVG-a); već upisane ostaju. {@code renderovano} broji uspele ({@code OK}) rendere,
     * {@code neuspelo} upisane {@code GRESKA}, {@code preostalo} samo zastarele koje nisu stigle na red, pa front koji zove
     * dok je {@code preostalo > 0} uvek stane. Jedan poziv po predmetu u isto vreme (u memoriji, jedan backend): drugi je 409.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RenderujSveInfo renderujSve(Long predmetId) {
        if (predmetId == null) {
            throw new SystemException(GrupaZadatakaService.PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        if (!renderSvihUToku.add(predmetId)) {
            throw new SystemException(RENDER_SVIH_U_TOKU, HttpStatus.CONFLICT);
        }
        try {
            Kandidati kandidati = citaj(() -> kandidati(predmetId));
            Instant rok = clock.instant().plus(MAX_TRAJANJE_RENDERA_SVIH);
            int obradjeno = 0;
            int renderovano = 0;
            int neuspelo = 0;
            for (Long id : kandidati.redom()) {
                if (obradjeno == MAX_RENDERA_PO_POZIVU || !clock.instant().isBefore(rok)) {
                    break;
                }
                obradjeno++;
                switch (renderujJednu(id)) {
                    case USPEO -> renderovano++;
                    case PAO -> neuspelo++;
                    case PRESKOCEN -> { }
                }
            }
            int preostalo = Math.max(0, kandidati.zastarelih() - obradjeno);
            log.info("Render banke predmeta {}: renderovano={}, neuspelo={}, preostalo={}", predmetId, renderovano, neuspelo,
                    preostalo);
            return new RenderujSveInfo(renderovano, neuspelo, preostalo);
        } finally {
            renderSvihUToku.remove(predmetId);
        }
    }

    /** Šta je render cele banke upisao za jednu varijantu. */
    private enum UpisRendera { USPEO, PAO, PRESKOCEN }

    /**
     * Id-jevi za render cele banke, po id-ju: zastarele, a kad nijedne nema, pale istog sadržaja; {@code zastarelih} je broj
     * zastarelih (za {@code preostalo}).
     */
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
        // pale tek kad nema zastarelih: u toku posle izmene preambule (front zove dok je preostalo > 0) svaka pala se
        // renderuje i broji jednom, a ručni poziv bez zastarelih ih ponavlja
        return new Kandidati(zastarele.isEmpty() ? pale : zastarele, zastarele.size());
    }

    /** Jedna varijanta cele banke: upisan uspeh, upisana greška ili ništa (nestala, osvežena ili promenjena u međuvremenu). */
    private UpisRendera renderujJednu(Long id) {
        Ulaz ulaz = citaj(() -> varijantaRepository.findById(id).map(this::ulazAkoTreba).orElse(null));
        if (ulaz == null) {
            return UpisRendera.PRESKOCEN;   // nestala ili ju je neko u međuvremenu renderovao
        }
        String hash = hash(ulaz);
        try {
            RenderVarijante.Ishod ishod = renderVarijante.renderuj(ulaz.preambula(), ulaz.tekst(), ulaz.resenje(), ulaz.prilozi());
            return pisi(() -> upisiAkoIsti(id, hash, ishod));
        } catch (RuntimeException e) {
            if (jeInfrastrukturna(e)) {
                throw e;   // pun disk ili pad baze ne sme da označi celu banku kao neuspelu
            }
            return oznaciGresku(id, hash, e) ? UpisRendera.PAO : UpisRendera.PRESKOCEN;
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

    /** Pod ključem: upis rendera samo za isti ulaz i kad ga u međuvremenu niko drugi nije osvežio; status se ne menja. */
    private UpisRendera upisiAkoIsti(Long id, String hash, RenderVarijante.Ishod ishod) {
        Optional<Varijanta> zakljucana = zakljucajAkoPostoji(id);
        if (zakljucana.isEmpty()) {
            return UpisRendera.PRESKOCEN;
        }
        Varijanta v = zakljucana.get();
        String sada = hash(ulaz(v));
        if (!sada.equals(hash) || (sada.equals(v.getRenderHash()) && v.getRenderStatus() == RenderStatus.OK)) {
            return UpisRendera.PRESKOCEN;
        }
        primeniRender(v, hash, ishod, false);
        return ishod.ok() ? UpisRendera.USPEO : UpisRendera.PAO;
    }

    /**
     * Greška pre ili posle rendera jedne varijante cele banke: upisuje se kao GRESKA (bez dela, status ostaje), u novoj
     * transakciji. Vraća da li je upisana.
     */
    private boolean oznaciGresku(Long id, String hash, RuntimeException uzrok) {
        String poruka = uzrok instanceof SystemException && uzrok.getMessage() != null ? uzrok.getMessage() : RENDER_PAO;
        if (uzrok instanceof SystemException) {
            log.warn("Render banke: varijanta {} nije renderovana: {}", id, poruka);
        } else {
            log.warn("Render banke: varijanta {} nije renderovana", id, uzrok);
        }
        RenderVarijante.Ishod greska = new RenderVarijante.Ishod(false, null, null, new RenderGreskaInfo(null, poruka), null);
        try {
            return pisi(() -> {
                Optional<Varijanta> zaUpis = zakljucajAkoPostoji(id)
                        .filter(v -> hash(ulaz(v)).equals(hash))
                        .filter(v -> !(hash.equals(v.getRenderHash()) && v.getRenderStatus() == RenderStatus.OK));   // neko ju je u međuvremenu uspešno renderovao
                zaUpis.ifPresent(v -> primeniRender(v, hash, greska, false));
                return zaUpis.isPresent();
            });
        } catch (RuntimeException e) {
            log.warn("Render banke: greška varijante {} nije upisana", id, e);
            return false;
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

    /**
     * Klodova izmena pre ključa: spojen i proveren sadržaj, preambula, da li je to sopstveni neodobren nacrt (izmena na
     * mestu; samo za odluku o kešu, korak 3 odlučuje ponovo nad zaključanim stanjem) i poslednji render (keš važi samo za
     * izmenu na mestu).
     */
    private record Spojeno(Sadrzaj sadrzaj, String preambula, boolean naMestu, String renderHash, RenderStatus renderStatus) {
    }

    private static Ulaz ulaz(Varijanta v) {
        return new Ulaz(v.getGrupa().getPredmet().getLatexPreambula(), v.getTekst(), v.getResenje(), prilozi(v));
    }

    private String hash(Ulaz u) {
        return renderVarijante.hash(u.preambula(), u.tekst(), u.resenje(), u.prilozi());
    }

    /** Render van transakcije i bez ključa, samo SVG. */
    private Prerender renderuj(Ulaz u) {
        return renderuj(u, false);
    }

    /** Render van transakcije i bez ključa; uz {@code png} svaki deo je jedno prevođenje u SVG i PNG. */
    private Prerender renderuj(Ulaz u, boolean png) {
        RenderVarijante.Ishod ishod = png
                ? renderVarijante.renderuj(u.preambula(), u.tekst(), u.resenje(), u.prilozi(), true, true)
                : renderVarijante.renderuj(u.preambula(), u.tekst(), u.resenje(), u.prilozi());
        return new Prerender(hash(u), ishod);
    }

    /**
     * Pod ključem: heš zaključanog sadržaja sa trenutnom preambulom. Isti heš i {@code OK} (bez {@code prisilno}) =
     * ništa; render pre ključa za isti heš se upisuje; inače (preambula ili sadržaj promenjeni posle čitanja, retko) render
     * pod ključem, a za MCP upise ({@code bezRenderaPodKljucem}) 409 {@value #IZMENJENA_U_MEDJUVREMENU}, pa se transakcija
     * poništava i ništa se ne upisuje. Vraća upisan ishod rendera, {@code null} kad je važio keš.
     */
    private RenderVarijante.Ishod primeniPosleKljuca(Varijanta v, Prerender pre, boolean prisilno,
                                                     boolean bezRenderaPodKljucem) {
        Ulaz ulaz = ulaz(v);
        String hash = hash(ulaz);
        if (!prisilno && hash.equals(v.getRenderHash()) && v.getRenderStatus() == RenderStatus.OK) {
            return null;
        }
        RenderVarijante.Ishod ishod;
        if (pre != null && hash.equals(pre.hash())) {
            ishod = pre.ishod();
        } else if (bezRenderaPodKljucem) {
            log.info("Varijanta {}: ulaz rendera se promenio posle čitanja, MCP upis odbijen bez rendera pod ključem", v.getId());
            throw new SystemException(IZMENJENA_U_MEDJUVREMENU, HttpStatus.CONFLICT);
        } else {
            log.info("Varijanta {}: ulaz rendera se promenio posle čitanja, render pod ključem", v.getId());
            ishod = renderVarijante.renderuj(ulaz.preambula(), ulaz.tekst(), ulaz.resenje(), ulaz.prilozi());
        }
        primeniRender(v, hash, ishod, true);
        return ishod;
    }

    /** PNG upisanog rendera kad je tražen; bez PNG-a zadatka (keš, pao tekst, PNG nije tražen) {@code null}. */
    private static SlikeRendera slike(boolean png, RenderVarijante.Ishod ishod) {
        return png && ishod != null && ishod.png() != null ? new SlikeRendera(ishod.png(), ishod.resenjePng()) : null;
    }

    /**
     * Upisuje ishod rendera. Neuspeh: {@code GRESKA} i poruka, stari SVG par ostaje do sledećeg uspešnog rendera (ni SVG
     * zadatka koji se preveo kad je palo samo rešenje ne zamenjuje stari: zadatak i rešenje su uvek iz istog rendera), a
     * AKTIVNA pada u NACRT samo kad je {@code spustiAktivnu} (čuvanje jedne varijante, ne render cele banke). Uspeh upisuje
     * nove SVG medije; stari se brišu samo kad varijanta nikad nije bila odobrena (snimak runde čuva id-jeve SVG-ova
     * odobrene, vidi <b>Render</b>).
     */
    private void primeniRender(Varijanta v, String hash, RenderVarijante.Ishod ishod, boolean spustiAktivnu) {
        v.setRenderHash(hash);
        if (!ishod.ok()) {
            v.setRenderStatus(RenderStatus.GRESKA);
            v.setRenderGreska(RenderVarijante.uKolonu(ishod.deo(), ishod.greska()));
            if (spustiAktivnu && v.getStatus() == StatusVarijante.AKTIVNA) {
                promeniStatus(v, StatusVarijante.NACRT);
            }
            return;
        }
        List<String> stari = v.isIkadOdobrena() ? List.of() : svgIds(v);
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
        return new Sadrzaj(naziv, cmd.tekst(), resenje, odgovori, json, proveriPriloge(cmd.prilozi(), cmd.tekst(), resenje));
    }

    /**
     * Do {@value #MAX_PRILOGA} priloga, ime po {@code BankaPP.proveriImePriloga}, ne {@value #REZERVISANO}, jedinstveno;
     * medij postoji i otpremljen je kao prilog ({@code POST /banka/prilozi}: {@code PRILOG_SLIKA} PNG/JPEG ili
     * {@code PRILOG_PDF}; slika slajda je javna, render SVG LaTeX ne uključuje); ukupno, kao base64 sa tekstom i rešenjem,
     * najviše 40 MB (telo zahteva render servisu), tj. oko 30 MB fajlova.
     */
    private List<Prilog> proveriPriloge(List<PrilogCmd> prilozi, String tekst, String resenje) {
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
            proveriVrstu(medij);
            rezultat.add(new Prilog(p.ime(), medij));
        }
        long ukupno = bajtova(tekst) + bajtova(resenje);
        for (Prilog p : rezultat) {
            ukupno += (p.medij().getVelicina() + 2) / 3 * 4;   // base64
        }
        if (ukupno > MAX_PRILOZI_ZA_RENDER_BAJTOVA) {
            throw new SystemException(PRILOZI_PREVELIKI, HttpStatus.BAD_REQUEST);
        }
        return rezultat;
    }

    /** Prilog je samo medij otpremljen kao prilog, i to PNG, JPEG ili PDF (po mime-u iz baze). */
    private static void proveriVrstu(Medij m) {
        boolean zaLatex = switch (m.getVrsta()) {
            case PRILOG_SLIKA -> "image/png".equals(m.getMime()) || "image/jpeg".equals(m.getMime());
            case PRILOG_PDF -> "application/pdf".equals(m.getMime());
            case SLIKA, RENDER_SVG -> throw new SystemException(NIJE_PRILOG, HttpStatus.BAD_REQUEST);
        };
        if (!zaLatex) {
            throw new SystemException(RenderVarijante.NEDOZVOLJEN_PRILOG, HttpStatus.BAD_REQUEST);
        }
    }

    private static long bajtova(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
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

    /**
     * Komanda iz stanja varijante i Klodovog patch-a: polje koje patch ne nosi ostaje kakvo jeste, {@code null} ga briše
     * (pravila brisanja nosi alat; servis spojeno proverava kao punu komandu). Izvor se izmenom ne menja.
     */
    private static VarijantaCmd spoji(Varijanta v, VarijantaPatch p) {
        return new VarijantaCmd(p.naziv().primeni(v.getNaziv()), p.tekst().primeni(v.getTekst()),
                p.resenje().primeni(v.getResenje()), p.odgovori().primeni(PoljaJson.odgovoriIzKolone(v.getOdgovori())),
                p.prilozi().primeni(prilozi(v)), null);
    }

    /** Varijanta koju Klod sme da menja na mestu i da briše: NACRT, CLAUDE i nikad odobrena (spec MCP 4.1). */
    private static boolean sopstveniNacrt(Varijanta v) {
        return v.getStatus() == StatusVarijante.NACRT && v.getIzvor() == IzvorVarijante.CLAUDE && !v.isIkadOdobrena();
    }

    /** Nov red na kraju grupe ({@code rb} = najveći + 1): NACRT, render CEKA; sadržaj postavlja pozivalac. */
    private Varijanta novaUGrupi(GrupaZadataka g, IzvorVarijante izvor, String serija) {
        LocalDateTime sada = sada();
        Varijanta v = new Varijanta();
        v.setGrupa(g);
        v.setRb(varijantaRepository.maxRb(g.getId()) + 1);
        v.setStatus(StatusVarijante.NACRT);
        v.setIzvor(izvor);
        v.setSerija(serija);
        v.setRenderStatus(RenderStatus.CEKA);
        v.setKreirano(sada);
        v.setIzmenjeno(sada);
        return v;
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
                v.getRenderStatus(), id(v.getRenderSvg()), id(v.getRenderResenjeSvg()), v.getIzmenjeno(), v.isIkadOdobrena(),
                zamenjujeId(v), v.getSerija());
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
                greska == null ? null : greska.info(), greska == null ? null : greska.deo(), v.getKreirano(), v.getIzmenjeno(),
                v.isIkadOdobrena(), zamenjujeId(v), v.getSerija());
    }

    /** Id originala bez učitavanja (lenja veza: id je u proksiju). */
    private static Long zamenjujeId(Varijanta v) {
        return v.getZamenjuje() == null ? null : v.getZamenjuje().getId();
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
