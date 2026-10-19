package tri.novica.gfssystem.mcp.servis;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.banka.BankaFilter;
import tri.novica.gfssystem.dto.banka.BankaPretragaStavka;
import tri.novica.gfssystem.dto.banka.GrupaCvor;
import tri.novica.gfssystem.dto.banka.GrupaDetails;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaCmd;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaInfo;
import tri.novica.gfssystem.dto.banka.SlikeRendera;
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
import tri.novica.gfssystem.service.banka.VarijantaService;

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

    private final McpLinkovi linkovi;
    private final McpBudzet budzet;
    private final GrupaZadatakaService grupaService;
    private final VarijantaService varijantaService;
    private final BankaPretragaService pretragaService;
    private final IskljuceneTemeService temeService;

    public McpBanka(McpLinkovi linkovi, McpBudzet budzet, GrupaZadatakaService grupaService,
                    VarijantaService varijantaService, BankaPretragaService pretragaService,
                    IskljuceneTemeService temeService) {
        this.linkovi = linkovi;
        this.budzet = budzet;
        this.grupaService = grupaService;
        this.varijantaService = varijantaService;
        this.pretragaService = pretragaService;
        this.temeService = temeService;
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

    // ---------------------------------------------------------------- pomoćno

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
