package tri.novica.gfssystem.mcp.servis;

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
import tri.novica.gfssystem.dto.banka.SlikeRendera;
import tri.novica.gfssystem.dto.banka.StavkaPonistavanja;
import tri.novica.gfssystem.dto.banka.VarijantaDetails;
import tri.novica.gfssystem.dto.banka.VarijantaInfo;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.McpBudzet;
import tri.novica.gfssystem.mcp.McpLinkovi;
import tri.novica.gfssystem.mcp.McpPoziv;
import tri.novica.gfssystem.mcp.McpZahtev;
import tri.novica.gfssystem.service.banka.BankaPretragaService;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.service.banka.IskljuceneTemeService;
import tri.novica.gfssystem.service.banka.KopijaGrupeService;
import tri.novica.gfssystem.service.banka.SerijaService;
import tri.novica.gfssystem.service.banka.VarijantaService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.utility.Serija;

import java.util.ArrayList;
import java.util.List;
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
 * Pravljenje troši dnevni budžet ključa pre upisa (grupa 1, kopija broj stavki podstabla). Svaki upis nosi ispravnu
 * seriju ({@link Serija#proveri}); izmena, premeštanje i brisanje je ne upisuju (serija grupe je ona iz pravljenja).
 */
@Service
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

    private final McpLinkovi linkovi;
    private final McpBudzet budzet;
    private final GrupaZadatakaService grupaService;
    private final VarijantaService varijantaService;
    private final BankaPretragaService pretragaService;
    private final IskljuceneTemeService temeService;
    private final KopijaGrupeService kopijaService;
    private final SerijaService serijaService;
    private final PrezentacijaService prezentacijaService;

    public McpBanka(McpLinkovi linkovi, McpBudzet budzet, GrupaZadatakaService grupaService,
                    VarijantaService varijantaService, BankaPretragaService pretragaService,
                    IskljuceneTemeService temeService, KopijaGrupeService kopijaService, SerijaService serijaService,
                    PrezentacijaService prezentacijaService) {
        this.linkovi = linkovi;
        this.budzet = budzet;
        this.grupaService = grupaService;
        this.varijantaService = varijantaService;
        this.pretragaService = pretragaService;
        this.temeService = temeService;
        this.kopijaService = kopijaService;
        this.serijaService = serijaService;
        this.prezentacijaService = prezentacijaService;
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
     * varijante nacrti iz serije). Troši budžet za sve stavke kopije (grupe i nearhivirane varijante) pre upisa.
     */
    public GrupaDetails kopirajGrupu(Long id, Namena ciljNamena, Long ciljRoditeljId, String serija) {
        Serija.proveri(serija);
        Long grupaId = obavezan(id, "grupaId");
        budzet.trosiPravljenje(McpZahtev.oznaka(), kopijaService.brojStavki(grupaId));
        return kopijaService.kopiraj(grupaId, ciljNamena, ciljRoditeljId, serija);
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
