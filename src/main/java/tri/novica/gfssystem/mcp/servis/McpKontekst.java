package tri.novica.gfssystem.mcp.servis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.banka.GrupaCvor;
import tri.novica.gfssystem.dto.banka.PreambulaInfo;
import tri.novica.gfssystem.dto.domaci.DomaciTekst;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.McpLinkovi;
import tri.novica.gfssystem.service.DomaciService;
import tri.novica.gfssystem.service.PredmetService;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.service.banka.IskljuceneTemeService;
import tri.novica.gfssystem.service.banka.LatexPreambulaService;
import tri.novica.gfssystem.service.banka.SerijaService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fasada: Kontekst i vodič za Kloda (spec MCP 4.2): predmeti, okruženje, preambula, tekstovi domaćih.
 * Jedino mesto gde MCP dodiruje postojeće servise; zove samo metode sa
 * {@code McpOpsegTest.DOZVOLJENE_METODE}, a skup njenih javnih metoda je zakucan u {@code McpFasadeTest.METODE}.
 * Sve je samo čitanje. Vodiči se čitaju jednom, pri startu ({@link VodicTema#ucitaj}): neispravan tekst ruši start.
 */
@Service
public class McpKontekst {

    /** Koliko najnovijih serija vraća {@code kontekst}. */
    static final int BROJ_SERIJA = 5;
    /** Tekstova domaćih po strani u {@code domaci_tekstovi}. */
    static final int DOMACIH_PO_STRANI = 20;
    static final String PREDMET_OBAVEZAN = "Predmet je obavezan.";
    static final String PREDMET_NIJE_PRONADJEN = "Predmet nije pronađen.";

    private final String okruzenje;
    private final McpLinkovi linkovi;
    private final PredmetService predmetService;
    private final GrupaZadatakaService grupaService;
    private final PrezentacijaService prezentacijaService;
    private final IskljuceneTemeService temeService;
    private final LatexPreambulaService preambulaService;
    private final SerijaService serijaService;
    private final DomaciService domaciService;
    private final Map<VodicTema, String> vodici = new EnumMap<>(VodicTema.class);

    public McpKontekst(@Value("${gfs.okruzenje}") String okruzenje, McpLinkovi linkovi, PredmetService predmetService,
                       GrupaZadatakaService grupaService, PrezentacijaService prezentacijaService,
                       IskljuceneTemeService temeService, LatexPreambulaService preambulaService,
                       SerijaService serijaService, DomaciService domaciService) {
        this.okruzenje = okruzenje;
        this.linkovi = linkovi;
        this.predmetService = predmetService;
        this.grupaService = grupaService;
        this.prezentacijaService = prezentacijaService;
        this.temeService = temeService;
        this.preambulaService = preambulaService;
        this.serijaService = serijaService;
        this.domaciService = domaciService;
        for (VodicTema tema : VodicTema.values()) {
            vodici.put(tema, tema.ucitaj());
        }
    }

    /**
     * Prvi poziv u razgovoru: okruženje, link UI-ja, svi predmeti po id-ju (koreni banke obe namene sa brojačima, broj
     * prezentacija, isključene teme, sopstvena preambula) i {@value #BROJ_SERIJA} najnovijih serija preko svih predmeta.
     */
    public McpKontekstInfo kontekst() {
        List<McpPredmetKontekst> predmeti = predmetService.findAll().stream()
                .sorted(Comparator.comparing(PredmetInfo::getId))
                .map(this::predmet)
                .toList();
        return new McpKontekstInfo(okruzenje, linkovi.baza(), predmeti, serijaService.poslednje(BROJ_SERIJA));
    }

    /** Tekst vodiča za temu ({@code banka}, {@code pitanja}, {@code pravila}); nepoznata tema je 400. */
    public String vodic(String tema) {
        return vodici.get(VodicTema.iz(tema));
    }

    /** Efektivna preambula predmeta, samo čitanje (menja je Novica u UI-ju). */
    public PreambulaInfo preambula(Long predmetId) {
        return preambulaService.preambula(obavezan(predmetId));
    }

    /**
     * Tekstovi domaćih predmeta kao izvor za banku (id, naslov, tekst, datum; nikad grupe, studenti ni predaje), najnoviji
     * prvi, {@value #DOMACIH_PO_STRANI} po strani; {@code strana} od 1. Nepostojeći predmet je 404, ne prazna lista, da Klod
     * pogrešan id ne pročita kao "nema domaćih".
     */
    public McpStrana<DomaciTekst> domaciTekstovi(Long predmetId, int strana) {
        Long id = obavezan(predmetId);
        if (predmetService.findAll().stream().noneMatch(p -> id.equals(p.getId()))) {
            throw new SystemException(PREDMET_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
        }
        return McpStrana.iz(domaciService.tekstoviPredmeta(id, McpStrana.zahtev(strana, DOMACIH_PO_STRANI)));
    }

    private McpPredmetKontekst predmet(PredmetInfo p) {
        List<McpKorenBanke> koreni = new ArrayList<>();
        for (Namena namena : Namena.values()) {
            for (GrupaCvor c : grupaService.stablo(p.getId(), namena)) {
                koreni.add(new McpKorenBanke(c.id(), c.naziv(), namena, c.brojVarijanti(), c.brojAktivnih(),
                        c.brojNacrta(), c.brojClaudeNacrta(), c.brojGresaka()));
            }
        }
        return new McpPredmetKontekst(p.getId(), p.getNaziv(), koreni, prezentacijaService.lista(p.getId()).size(),
                temeService.lista(p.getId()), !preambulaService.preambula(p.getId()).podrazumevana());
    }

    private static Long obavezan(Long predmetId) {
        if (predmetId == null) {
            throw new SystemException(PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        return predmetId;
    }
}
