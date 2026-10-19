package tri.novica.gfssystem.mcp.servis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.banka.BankaFilter;
import tri.novica.gfssystem.dto.banka.BankaPretragaStavka;
import tri.novica.gfssystem.dto.banka.GrupaCmd;
import tri.novica.gfssystem.dto.banka.GrupaCvor;
import tri.novica.gfssystem.dto.banka.GrupaDetails;
import tri.novica.gfssystem.dto.banka.GrupaPatch;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaCmd;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaInfo;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PonistavanjeInfo;
import tri.novica.gfssystem.dto.banka.PremestiCmd;
import tri.novica.gfssystem.dto.banka.ProbaSaSlikom;
import tri.novica.gfssystem.dto.banka.RenderProbaCmd;
import tri.novica.gfssystem.dto.banka.RenderProbaInfo;
import tri.novica.gfssystem.dto.banka.SlikeRendera;
import tri.novica.gfssystem.dto.banka.StavkaPonistavanja;
import tri.novica.gfssystem.dto.banka.UpisVarijante;
import tri.novica.gfssystem.dto.banka.VarijantaCmd;
import tri.novica.gfssystem.dto.banka.VarijantaDetails;
import tri.novica.gfssystem.dto.banka.VarijantaInfo;
import tri.novica.gfssystem.dto.banka.VarijantaPatch;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.McpBudzet;
import tri.novica.gfssystem.mcp.McpGreske;
import tri.novica.gfssystem.mcp.McpLinkovi;
import tri.novica.gfssystem.mcp.McpPoziv;
import tri.novica.gfssystem.mcp.McpZahtev;
import tri.novica.gfssystem.service.banka.BankaPretragaService;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.service.banka.IskljuceneTemeService;
import tri.novica.gfssystem.service.banka.KopijaGrupeService;
import tri.novica.gfssystem.service.banka.SerijaService;
import tri.novica.gfssystem.service.banka.VarijantaService;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.utility.Serija;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fasada: Banka zadataka za Kloda (spec MCP 4.3): grupe, varijante, proba rendera, isključene teme, poništavanje
 * serije. Jedino mesto gde MCP dodiruje postojeće servise; zove samo metode sa
 * {@code McpOpsegTest.DOZVOLJENE_METODE}, a skup njenih javnih metoda je zakucan u {@code McpFasadeTest.METODE}.
 * <p>
 * Čitanje vraća MCP DTO-ove bez id-jeva SVG-a ({@link McpVarijanta}, {@link McpVarijantaSazetak}); stablo, grupa i
 * pretraga ih ni nemaju. Slika varijante je PNG sačuvanog sadržaja ({@code VarijantaService.slika}, ništa se ne upisuje),
 * spojen kroz {@link McpSlika}: za grupu namene TEST samo zadatak osim uz traženo rešenje, za PREDAVANJE zadatak i
 * rešenje. Slika koja ne stigne (render ne radi, sva mesta za probe zauzeta, PNG se ne čita, rok) nije greška alata:
 * podaci idu uz napomenu {@value #SLIKA_NIJE_STIGLA}.
 * <p>
 * Grupe (spec MCP 4.1): Klod pravi grupe (izvor CLAUDE, serija), i pod Novicinom grupom; preimenuje, menja šemu polja,
 * premešta i briše samo svoje ({@link #klodova}: izvor se nikad ne menja, pa provera pre ključa servisa nema trku).
 * Pravljenje troši dnevni budžet ključa pre upisa (grupa 1, kopija broj stavki podstabla), posle jeftinih provera bez ključa
 * (postojanje izvora, cilj kopije); pokušaj koji servis zatim odbije (409, 400 sadržaja) ostaje potrošen. Svaki upis nosi
 * ispravnu seriju ({@link Serija#proveri}); izmena, premeštanje i brisanje je ne upisuju (serija grupe je ona iz pravljenja).
 * <p>
 * Varijante (spec MCP 4.1): Klod pravi nacrte (NACRT, izvor CLAUDE bez obzira na komandu, serija), na mestu menja i briše
 * samo sopstveni neodobren nacrt, a izmena svake druge varijante je kopija-nacrt koja je zamenjuje ({@code VarijantaService}
 * odlučuje ponovo pod ključem). Nova varijanta, kopija i duplikat troše po jedno pravljenje pre upisa. {@code UpisVarijante}
 * i {@code VarijantaDetails} se ovde prevode u {@link McpUpisVarijante} i {@link McpVarijanta}, pa alat ne vidi id-jeve
 * SVG-a; PNG upisa i probe ide kroz {@link McpSlika} po istom pravilu namene kao {@link #varijanta}.
 * <p>
 * Prilozi (spec MCP 4.5): fajl stiže kroz {@code POST /mcp/prilozi} ({@code McpUploadRest}), ne kroz argument alata;
 * {@link #sacuvajPrilog} troši dnevni budžet upload-a pre upisa, a tip prepoznaje {@code MedijService} po prvim bajtovima.
 */
@Service
@Slf4j
public class McpBanka {

    /** Napomena uz uspeh bez slike (spec MCP 4.1). */
    static final String SLIKA_NIJE_STIGLA = "Slika nije stigla na vreme.";
    /** Stavki pretrage po strani u {@code pretraga_banke}. */
    static final int PRETRAGE_PO_STRANI = 25;
    /** Kao podrazumevani sort REST pretrage, sa {@code id} na kraju da redosled bude isti između strana. */
    static final Sort SORT_PRETRAGE = Sort.by(Sort.Order.desc("izmenjeno"), Sort.Order.desc("id"));
    /** Render servis ne radi (503) ili su sva mesta za probe zauzeta (429): slika ne stiže, alat uspeva. */
    private static final Set<Integer> SLIKA_NEDOSTUPNA = Set.of(HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.TOO_MANY_REQUESTS.value());
    private static final String PREDMET_OBAVEZAN = "Predmet je obavezan.";
    /** 409 za izmenu, šemu polja, premeštanje i brisanje Novicine (RUCNO) grupe. */
    static final String NOVICINA_GRUPA = "Ovu grupu je napravio Novica; izmenu radi on u UI-ju.";
    /** 400 za {@code izmeni_varijantu} bez ijednog polja: nad tuđom varijantom bi to bila kopija bez izmene. */
    static final String IZMENA_BEZ_POLJA = "Izmena mora sadržati bar jedno polje varijante.";

    private final McpLinkovi linkovi;
    private final McpBudzet budzet;
    private final GrupaZadatakaService grupaService;
    private final VarijantaService varijantaService;
    private final BankaPretragaService pretragaService;
    private final IskljuceneTemeService temeService;
    private final KopijaGrupeService kopijaService;
    private final SerijaService serijaService;
    private final PrezentacijaService prezentacijaService;
    private final MedijService medijService;

    public McpBanka(McpLinkovi linkovi, McpBudzet budzet, GrupaZadatakaService grupaService,
                    VarijantaService varijantaService, BankaPretragaService pretragaService,
                    IskljuceneTemeService temeService, KopijaGrupeService kopijaService, SerijaService serijaService,
                    PrezentacijaService prezentacijaService, MedijService medijService) {
        this.linkovi = linkovi;
        this.budzet = budzet;
        this.grupaService = grupaService;
        this.varijantaService = varijantaService;
        this.pretragaService = pretragaService;
        this.temeService = temeService;
        this.kopijaService = kopijaService;
        this.serijaService = serijaService;
        this.prezentacijaService = prezentacijaService;
        this.medijService = medijService;
    }

    // ---------------------------------------------------------------- čitanje

    /** Celo stablo predmeta i namene sa brojačima, izvorom grupe i isključenošću (grupa ili predak). */
    public List<GrupaCvor> stablo(Long predmetId, Namena namena) {
        return grupaService.stablo(predmetId, namena);
    }

    /** Grupa sa sažetkom svih varijanti (bez teksta i SVG-a) i linkom u UI. */
    public McpGrupa grupa(Long grupaId) {
        Long id = obavezan(grupaId, "grupaId");
        GrupaDetails grupa = grupaService.detalji(id);
        List<McpVarijantaSazetak> varijante = varijantaService.lista(id, null).stream()
                .map(McpBanka::sazetak)
                .toList();
        return new McpGrupa(grupa, varijante, linkovi.grupa(id));
    }

    /**
     * Varijanta za Kloda i, kad je {@code slika.slika()}, PNG sačuvanog sadržaja: rešenje ulazi u sliku za PREDAVANJE
     * uvek, za TEST samo uz {@code slika.resenje()}. Slika se traži samo dok poziv ima vremena
     * ({@link McpPoziv#imaVremenaZaSliku()}); render koji ne radi (503), zauzeta mesta za probe (429) i PNG koji se ne čita
     * ili ga nema (tekst se ne prevodi) daju podatke sa napomenom {@value #SLIKA_NIJE_STIGLA}. Druga greška rendera
     * (npr. prilog bez fajla) je greška alata.
     */
    public McpSaSlikom<McpVarijanta> varijanta(Long id, ZahtevSlike slika, McpPoziv poziv) {
        Long varijantaId = obavezan(id, "varijantaId");
        VarijantaDetails d = varijantaService.detalji(varijantaId);
        Namena namena = grupaService.detalji(d.grupaId()).namena();
        McpVarijanta varijanta = mcp(d, namena);
        if (slika == null || !slika.slika()) {
            return new McpSaSlikom<>(varijanta, null, null);
        }
        byte[] png = poziv.imaVremenaZaSliku()
                ? png(varijantaId, namena == Namena.PREDAVANJE || slika.resenje())
                : null;
        return new McpSaSlikom<>(varijanta, png, png == null ? SLIKA_NIJE_STIGLA : null);
    }

    /**
     * Pretraga banke predmeta (predmet je obavezan), {@value #PRETRAGE_PO_STRANI} po strani, najnovije izmenjene prve;
     * {@code strana} od 1. Filter je proveren pri konstrukciji ({@code q} bar 2 znaka, serija po obrascu).
     */
    public McpStrana<BankaPretragaStavka> pretraga(BankaFilter filter, int strana) {
        if (filter == null || filter.predmetId() == null) {
            throw new SystemException(PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        return McpStrana.iz(pretragaService.pretraga(filter, McpStrana.zahtev(strana, PRETRAGE_PO_STRANI, SORT_PRETRAGE)));
    }

    /** Isključene teme predmeta, najnovije prve. */
    public List<IskljucenaTemaInfo> iskljuceneTeme(Long predmetId) {
        return temeService.lista(predmetId);
    }

    // ---------------------------------------------------------------- isključene teme

    /**
     * Klodovo isključenje teme (izvor CLAUDE, serija upisa): troši jedno pravljenje iz dnevnog budžeta ključa (pre upisa;
     * preko granice 429 i ništa se ne upisuje). Ponovljen poziv za istu grupu ili isti normalizovan opis bez grupe je 409,
     * nikad drugi red ({@code IskljuceneTemeService.dodaj}).
     */
    public IskljucenaTemaInfo iskljuciTemu(Long predmetId, String opis, Long grupaId, String serija) {
        budzet.trosiPravljenje(McpZahtev.oznaka(), 1);
        return temeService.dodaj(predmetId, new IskljucenaTemaCmd(opis, grupaId), IzvorVarijante.CLAUDE, serija);
    }

    /** Uključuje temu (briše isključenje, i Novicino); alat traži potvrdu korisnika. Nepoznata tema je 404. */
    public void ukljuciTemu(Long temaId) {
        temeService.obrisi(obavezan(temaId, "temaId"));
    }

    // ---------------------------------------------------------------- grupe

    /**
     * Klodova grupa (izvor CLAUDE, serija upisa): koren predmeta i namene ili podgrupa bilo koje grupe, i Novicine (pravila
     * stabla ostaju: podgrupa ne ide pod grupu sa varijantama). Troši jedno pravljenje iz budžeta pre upisa.
     */
    public GrupaDetails napraviGrupu(GrupaCmd cmd, String serija) {
        Serija.proveri(serija);
        budzet.trosiPravljenje(McpZahtev.oznaka(), 1);
        return grupaService.kreiraj(cmd, IzvorVarijante.CLAUDE, serija);
    }

    /** Delimična izmena naziva, opisa, težine i uputstva Klodove grupe (merge patch; šema polja ostaje). */
    public GrupaDetails izmeniGrupu(Long id, GrupaPatch patch, String serija) {
        Serija.proveri(serija);
        return grupaService.izmeniDelimicno(klodova(id), patch);
    }

    /**
     * Nova šema polja Klodove grupe; servis je pod ključevima prvo isproba i odbija sa 409 kad bi pogodila varijante
     * (izgubljen odgovor, AKTIVNA u NACRT).
     */
    public GrupaDetails izmeniPoljaGrupe(Long id, List<Polje> polja, String serija) {
        Serija.proveri(serija);
        return grupaService.izmeniPolja(klodova(id), polja);
    }

    /** Premešta Klodovu grupu sa podstablom (i pod Novicinu grupu: to je dopuna njenog podstabla). */
    public GrupaDetails premestiGrupu(Long id, PremestiCmd cmd, String serija) {
        Serija.proveri(serija);
        return grupaService.premesti(klodova(id), cmd);
    }

    /** Briše praznu Klodovu grupu; grupa sa varijantama ili podgrupama je 409 {@code Briše se samo prazna grupa.} */
    public void obrisiGrupu(Long id, String serija) {
        Serija.proveri(serija);
        grupaService.obrisiPraznu(klodova(id));
    }

    /**
     * Kopija grupe bilo kog izvora sa podstablom u namenu {@code ciljNamena} istog predmeta (kopije su Klodove: grupe CLAUDE,
     * varijante nacrti iz serije). Troši budžet za sve stavke kopije (grupe i nearhivirane varijante) pre upisa, a tek
     * posle provere bez ključa da izvor i cilj postoje i da je cilj u istom predmetu i nameni (pogrešan cilj ne troši;
     * servis sve to ponovo proverava pod ključem).
     */
    public GrupaDetails kopirajGrupu(Long id, Namena ciljNamena, Long ciljRoditeljId, String serija) {
        Serija.proveri(serija);
        Long grupaId = obavezan(id, "grupaId");
        if (ciljNamena == null) {
            throw new SystemException(GrupaZadatakaService.NAMENA_OBAVEZNA, HttpStatus.BAD_REQUEST);
        }
        if (ciljRoditeljId != null) {
            GrupaDetails cilj = grupaService.detalji(ciljRoditeljId);
            Long predmetIzvora = grupaService.detalji(grupaId).predmetId();
            if (cilj.namena() != ciljNamena || !cilj.predmetId().equals(predmetIzvora)) {
                throw new SystemException(KopijaGrupeService.CILJ_VAN_STABLA, HttpStatus.BAD_REQUEST);
            }
        }
        budzet.trosiPravljenje(McpZahtev.oznaka(), kopijaService.brojStavki(grupaId));
        return kopijaService.kopiraj(grupaId, ciljNamena, ciljRoditeljId, serija);
    }

    // ---------------------------------------------------------------- varijante

    /**
     * Klodova nova varijanta na kraj lista {@code grupaId} (i Novicinog): NACRT, izvor CLAUDE bez obzira na komandu, serija
     * upisa. Grupa se čita pre budžeta (nepostojeća je 404 bez troška), pa se troši jedno pravljenje pre upisa. Render je deo
     * upisa, uz traženu sliku i PNG iz istog prevođenja; greška rendera je stanje varijante ({@code renderGreska},
     * {@code renderDeo}), ne greška alata. Slika po {@link #saSlikom}; rezultat nosi {@code linkPregleda} serije.
     */
    public McpSaSlikom<McpUpisVarijante> napraviVarijantu(Long grupaId, VarijantaCmd cmd, String serija, ZahtevSlike slika,
                                                          McpPoziv poziv) {
        Serija.proveri(serija);
        Long id = obavezan(grupaId, "grupaId");
        if (cmd == null) {
            throw new SystemException(McpGreske.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        Namena namena = grupaService.detalji(id).namena();
        budzet.trosiPravljenje(McpZahtev.oznaka(), 1);
        ZahtevSlike zahtev = slika == null ? ZahtevSlike.NE : slika;
        // izvor CLAUDE upisuje servis (MCP kreiraj ne čita cmd.izvor())
        UpisVarijante upis = varijantaService.kreiraj(id, cmd, serija, zahtev.slika());
        return saSlikom(upis, namena, zahtev, poziv, linkovi.pregled(serija));
    }

    /**
     * Klodova delimična izmena (merge patch, {@code VarijantaService.izmeniDelimicno}): sopstveni neodobren nacrt se menja na
     * mestu, svaka druga varijanta dobija kopiju-nacrt iz serije koja je zamenjuje ({@code linkOriginala}; original ostaje).
     * Izmena bez ijednog polja je 400 {@value #IZMENA_BEZ_POLJA}. Kopija troši jedno pravljenje pre upisa: odluka se
     * predviđa iz stanja pre upisa, a servis je ponovo donosi pod ključem (nacrt koji Novica odobri baš tada postaje kopija
     * bez troška; retko, a budžet je zaštita od petlje). 409 servisa (sadržaj ili preambula promenjeni tokom rendera) je
     * greška alata bez slike, i ništa nije upisano.
     */
    public McpSaSlikom<McpUpisVarijante> izmeniVarijantu(Long id, VarijantaPatch patch, String serija, ZahtevSlike slika,
                                                         McpPoziv poziv) {
        Serija.proveri(serija);
        Long varijantaId = obavezan(id, "varijantaId");
        if (patch == null) {
            throw new SystemException(McpGreske.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        if (bezIzmene(patch)) {
            throw new SystemException(IZMENA_BEZ_POLJA, HttpStatus.BAD_REQUEST);
        }
        VarijantaDetails trenutna = varijantaService.detalji(varijantaId);
        Namena namena = grupaService.detalji(trenutna.grupaId()).namena();
        if (!sopstveniNacrt(trenutna)) {
            budzet.trosiPravljenje(McpZahtev.oznaka(), 1);
        }
        ZahtevSlike zahtev = slika == null ? ZahtevSlike.NE : slika;
        UpisVarijante upis = varijantaService.izmeniDelimicno(varijantaId, patch, serija, zahtev.slika());
        return saSlikom(upis, namena, zahtev, poziv, null);
    }

    /**
     * Duplikat bilo koje varijante (i Novicine) kao Klodov nacrt iz serije, na kraju iste grupe. Varijanta i namena njene grupe
     * se čitaju pre budžeta (nepostojeća je 404 bez troška), pa se troši jedno pravljenje pre upisa; servis čita ponovo, pod
     * ključem. Posle upisa se ništa ne čita: grupa obrisana u međuvremenu ne sme sačuvan duplikat da prikaže kao grešku.
     */
    public McpVarijanta duplirajVarijantu(Long id, String serija) {
        Serija.proveri(serija);
        Long varijantaId = obavezan(id, "varijantaId");
        Namena namena = grupaService.detalji(varijantaService.detalji(varijantaId).grupaId()).namena();
        budzet.trosiPravljenje(McpZahtev.oznaka(), 1);
        return mcp(varijantaService.dupliraj(varijantaId, serija), namena);
    }

    /**
     * Briše Klodov neodobren nacrt; svaka druga varijanta je 409 {@code Klod briše samo svoje neodobrene nacrte.} Odluku
     * donosi servis pod ključem, a varijanta se pre toga u istom zahtevu ne čita: brisanje radi u transakciji bez
     * čišćenja persistence context-a, pa bi ranije učitan entitet nosio staro stanje.
     */
    public void obrisiVarijantu(Long id, String serija) {
        Serija.proveri(serija);
        varijantaService.obrisiSopstveniNacrt(obavezan(id, "varijantaId"));
    }

    /**
     * Render-proba bez upisa ({@code VarijantaService.probaSaSlikom}: pravila i mesta za probe kao REST proba, samo PNG).
     * Uz {@code slika.slika()} slika ima zadatak i, uz {@code slika.resenje()}, poslato rešenje; tekst koji se nije preveo
     * nema sliku (greška je u rezultatu), a PNG koji se ne čita daje napomenu {@value #SLIKA_NIJE_STIGLA}. Render servis
     * koji ne radi (503) i zauzeta mesta za probe (429) su greška alata: bez rendera nema ni ishoda probe.
     */
    public McpSaSlikom<McpProba> probaRendera(RenderProbaCmd cmd, ZahtevSlike slika) {
        ProbaSaSlikom proba = varijantaService.probaSaSlikom(cmd);
        RenderProbaInfo info = proba.info();
        McpProba rezultat = new McpProba(info.ok(), info.greska(), info.deo());
        SlikeRendera slike = proba.slike();
        if (slika == null || !slika.slika() || slike == null || slike.zadatak() == null) {
            return new McpSaSlikom<>(rezultat, null, null);
        }
        byte[] png = McpSlika.spoji(slike.zadatak(), slika.resenje() ? slike.resenje() : null);
        return new McpSaSlikom<>(rezultat, png, png == null ? SLIKA_NIJE_STIGLA : null);
    }

    // ---------------------------------------------------------------- prilozi

    /**
     * Prilog zadatka ({@code POST /mcp/prilozi}): PNG i JPEG kao {@code PRILOG_SLIKA}, PDF kao {@code PRILOG_PDF}, po prvim
     * bajtovima (deklarisani MIME i ime se ne veruju; ostalo je 400 {@code Dozvoljeni prilozi: PNG, JPEG i PDF.}). Veličina
     * se troši iz dnevnog budžeta upload-a ključa pre upisa (preko granice 429 i ništa se ne upisuje); prilog ostaje van
     * javnog serviranja kao i Novicin. Vrsta se čita iz sačuvanog reda; jedan INFO red: oznaka ključa, serija, id, vrsta,
     * veličina.
     */
    public McpMedij sacuvajPrilog(byte[] sadrzaj, String naziv, String serija) {
        Serija.proveri(serija);
        String oznaka = McpZahtev.oznaka();
        budzet.trosiUpload(oznaka, sadrzaj == null ? 0 : sadrzaj.length);
        MedijInfo info = medijService.sacuvajPrilog(sadrzaj, naziv);
        VrstaMedija vrsta = medijService.vrsta(info.id())
                .orElseThrow(() -> new IllegalStateException("Sačuvan prilog bez reda: " + info.id()));
        // nikad ime fajla ni sadržaj
        log.info("MCP upload=prilog kljuc={} serija={} id={} vrsta={} velicina={}", oznaka, serija, info.id(), vrsta,
                info.velicina());
        return new McpMedij(info.id(), vrsta, info.mime(), info.velicina());
    }

    // ---------------------------------------------------------------- poništavanje serije

    /**
     * Poništava seriju u banci ({@code SerijaService.ponisti}) pa u prezentacijama ({@code PrezentacijaService.ponistiSeriju}),
     * svaki deo u svojim transakcijama; {@code obrisano} i {@code preskoceno} su nadovezani tim redom. {@code potvrdi=false}
     * ništa ne menja: {@code obrisano} je spisak onoga što bi se obrisalo.
     */
    public PonistavanjeInfo ponistiSeriju(String serija, boolean potvrdi) {
        Serija.proveri(serija);
        PonistavanjeInfo banka = serijaService.ponisti(serija, potvrdi);
        PonistavanjeInfo prezentacije = prezentacijaService.ponistiSeriju(serija, potvrdi);
        return new PonistavanjeInfo(potvrdi, spoji(banka.obrisano(), prezentacije.obrisano()),
                spoji(banka.preskoceno(), prezentacije.preskoceno()));
    }

    // ---------------------------------------------------------------- pomoćno

    /**
     * Id Klodove grupe; Novicina (RUCNO) je 409 {@value #NOVICINA_GRUPA}, nepostojeća 404. Čita grupu pre ključa servisa,
     * što je bezbedno jer se izvor grupe posle pravljenja ne menja (servis zatim prazni persistence context).
     */
    private Long klodova(Long id) {
        Long grupaId = obavezan(id, "grupaId");
        if (grupaService.detalji(grupaId).izvor() != IzvorVarijante.CLAUDE) {
            throw new SystemException(NOVICINA_GRUPA, HttpStatus.CONFLICT);
        }
        return grupaId;
    }

    /**
     * Upis varijante za Kloda sa slikom po pravilu slika (spec MCP 4.1): bez {@code slika.slika()} bez slike; za grupu namene
     * TEST samo zadatak osim uz {@code slika.resenje()}, za PREDAVANJE zadatak i rešenje. PNG je iz prevođenja samog upisa;
     * kad upis nije renderovao (keš: isti ulaz rendera, render OK) PNG sačuvanog sadržaja ({@code VarijantaService.slika}),
     * samo dok poziv ima vremena ({@link McpPoziv#imaVremenaZaSliku()}). Upis čiji se tekst nije preveo nema ni sliku ni
     * napomenu (greška sa linijom je u varijanti); slika koja ne stigne (rok, 503, 429, PNG koji se ne čita, bilo koja
     * greška slike posle upisa: {@link #pngPosleUpisa}) daje napomenu {@value #SLIKA_NIJE_STIGLA}, nikad grešku alata.
     */
    private McpSaSlikom<McpUpisVarijante> saSlikom(UpisVarijante upis, Namena namena, ZahtevSlike slika, McpPoziv poziv,
                                                   String linkPregleda) {
        McpUpisVarijante podaci = upis(upis, namena, linkPregleda);
        if (!slika.slika()) {
            return new McpSaSlikom<>(podaci, null, null);
        }
        boolean saResenjem = namena == Namena.PREDAVANJE || slika.resenje();
        VarijantaDetails d = upis.varijanta();
        SlikeRendera slike = upis.slike();
        byte[] png;
        if (slike != null) {
            png = McpSlika.spoji(slike.zadatak(), saResenjem ? slike.resenje() : null);
        } else if (d.renderStatus() != RenderStatus.OK) {
            return new McpSaSlikom<>(podaci, null, null);
        } else {
            png = poziv.imaVremenaZaSliku() ? pngPosleUpisa(d.id(), saResenjem) : null;
        }
        return new McpSaSlikom<>(podaci, png, png == null ? SLIKA_NIJE_STIGLA : null);
    }

    /**
     * {@link #png} posle sačuvanog upisa: upis je već commit-ovan, pa nijedna greška slike (i 404 za prilog bez fajla ili
     * varijantu obrisanu u međuvremenu, i neočekivan izuzetak) ne sme da ga prikaže kao neuspeh (Klod bi ga ponovio). Daje
     * {@code null}, pa napomenu {@value #SLIKA_NIJE_STIGLA}, uz WARN bez sadržaja ({@code SystemException} samo kod, ostalo
     * sa stack trace-om, da se prava greška vidi u logu).
     */
    private byte[] pngPosleUpisa(Long varijantaId, boolean saResenjem) {
        try {
            return png(varijantaId, saResenjem);
        } catch (SystemException e) {
            log.warn("MCP slika posle upisa nije stigla: varijanta={} greska=SystemException {}", varijantaId, e.getCode());
            return null;
        } catch (RuntimeException e) {
            log.warn("MCP slika posle upisa nije stigla: varijanta={}", varijantaId, e);
            return null;
        }
    }

    /** {@code UpisVarijante} za Kloda: radnja malim slovima, varijanta bez id-jeva SVG-a, linkovi. */
    private McpUpisVarijante upis(UpisVarijante upis, Namena namena, String linkPregleda) {
        VarijantaDetails d = upis.varijanta();
        String linkOriginala = upis.radnja() == UpisVarijante.Radnja.KOPIJA && d.zamenjuje() != null
                ? linkovi.varijanta(d.zamenjuje())
                : null;
        return new McpUpisVarijante(upis.radnja().name().toLowerCase(Locale.ROOT), mcp(d, namena),
                linkovi.varijanta(d.id()), linkOriginala, linkPregleda);
    }

    /** Kao {@code VarijantaService}: na mestu se menja i briše samo NACRT sa izvorom CLAUDE koji nikad nije odobren. */
    private static boolean sopstveniNacrt(VarijantaDetails d) {
        return d.status() == StatusVarijante.NACRT && d.izvor() == IzvorVarijante.CLAUDE && !d.ikadOdobrena();
    }

    private static boolean bezIzmene(VarijantaPatch p) {
        return !p.naziv().postavljeno() && !p.tekst().postavljeno() && !p.resenje().postavljeno()
                && !p.odgovori().postavljeno() && !p.prilozi().postavljeno();
    }

    private static List<StavkaPonistavanja> spoji(List<StavkaPonistavanja> prve, List<StavkaPonistavanja> druge) {
        List<StavkaPonistavanja> sve = new ArrayList<>(prve);
        sve.addAll(druge);
        return List.copyOf(sve);
    }

    /** PNG zadatka (i rešenja kad {@code saResenjem} i kad ga ima) ili {@code null} kad slika ne stiže. */
    private byte[] png(Long varijantaId, boolean saResenjem) {
        SlikeRendera slike;
        try {
            slike = varijantaService.slika(varijantaId);
        } catch (SystemException e) {
            if (e.getCode() != null && SLIKA_NEDOSTUPNA.contains(e.getCode())) {
                return null;
            }
            throw e;
        }
        if (slike == null || slike.zadatak() == null) {
            return null;
        }
        return McpSlika.spoji(slike.zadatak(), saResenjem ? slike.resenje() : null);
    }

    private McpVarijanta mcp(VarijantaDetails d, Namena namena) {
        List<McpPrilog> prilozi = d.prilozi().stream().map(p -> new McpPrilog(p.ime(), p.medij().id())).toList();
        return new McpVarijanta(d.id(), d.grupaId(), d.putanja(), namena, d.naziv(), d.tekst(), d.resenje(), d.odgovori(),
                d.polja(), prilozi, d.status(), d.izvor(), d.ikadOdobrena(), d.zamenjuje(), d.serija(), d.renderStatus(),
                d.renderGreska(), d.renderDeo(), d.izmenjeno(), linkovi.varijanta(d.id()));
    }

    private static McpVarijantaSazetak sazetak(VarijantaInfo v) {
        return new McpVarijantaSazetak(v.id(), v.naziv(), v.status(), v.izvor(), v.renderStatus(), v.zamenjuje());
    }

    /** Id iz argumenta alata; bez njega 400 {@code Neispravan parametar: <ime>.} (kao REST za pogrešan parametar). */
    private static Long obavezan(Long id, String ime) {
        if (id == null) {
            throw new SystemException("Neispravan parametar: " + ime + ".", HttpStatus.BAD_REQUEST);
        }
        return id;
    }
}
