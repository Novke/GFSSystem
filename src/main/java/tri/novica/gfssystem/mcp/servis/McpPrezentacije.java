package tri.novica.gfssystem.mcp.servis;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaDetails;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaInfo;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.McpBudzet;
import tri.novica.gfssystem.mcp.McpGreske;
import tri.novica.gfssystem.mcp.McpLinkovi;
import tri.novica.gfssystem.mcp.McpZahtev;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.utility.Serija;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Fasada: Prezentacije za Kloda (spec MCP 4.4): pregled, slajd, pravljenje prezentacije i INFO/PITANJE
 * slajda. Jedino mesto gde MCP dodiruje postojeće servise; zove samo metode sa
 * {@code McpOpsegTest.DOZVOLJENE_METODE}, a skup njenih javnih metoda je zakucan u {@code McpFasadeTest.METODE}.
 * <p>
 * Čitanje je sažeto ({@link PrezentacijaPregled}: strane koje staju u jedan odgovor) ili pun slajd bez poteza crteža
 * ({@link McpSlajd}). Klod pravi prezentacije i dodaje slajdove sa serijom; u A1 samo tipove iz {@link #DOZVOLJENI_TIPOVI}
 * (B dodaje ZADATAK, A2 PLATNO), a postojeće slajdove ne menja. Pravljenje troši jedno pravljenje iz dnevnog budžeta ključa
 * pre upisa; prezentacija koja se upravo izvodi se ne menja ({@code PrezentacijaService.dodajSlajdIzSerije}, 409).
 */
@Service
public class McpPrezentacije {

    /** Tipovi slajda koje Klod dodaje kroz {@code dodaj_slajd}. */
    static final Set<TipSlajda> DOZVOLJENI_TIPOVI = EnumSet.of(TipSlajda.INFO, TipSlajda.PITANJE);
    /** 400 za {@code dodaj_slajd} sa tipom van {@link #DOZVOLJENI_TIPOVI}. */
    static final String SAMO_INFO_I_PITANJE = "Klod u ovoj verziji dodaje samo INFO i PITANJE slajdove.";
    private static final String PREDMET_OBAVEZAN = "Predmet je obavezan.";

    private final McpLinkovi linkovi;
    private final McpBudzet budzet;
    private final PrezentacijaService prezentacijaService;

    public McpPrezentacije(McpLinkovi linkovi, McpBudzet budzet, PrezentacijaService prezentacijaService) {
        this.linkovi = linkovi;
        this.budzet = budzet;
        this.prezentacijaService = prezentacijaService;
    }

    // ---------------------------------------------------------------- čitanje

    /** Prezentacije predmeta (predmet je obavezan), najnovije izmenjene prve. */
    public List<PrezentacijaInfo> prezentacije(Long predmetId) {
        if (predmetId == null) {
            throw new SystemException(PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        return prezentacijaService.lista(predmetId);
    }

    /** Strana {@code strana} (od 1) sažetog pregleda prezentacije sa linkom u UI-ju. */
    public McpPrezentacijaPregled prezentacija(Long id, int strana) {
        Long prezentacijaId = obavezan(id, "prezentacijaId");
        return PrezentacijaPregled.pregled(prezentacijaService.detalji(prezentacijaId), strana,
                linkovi.prezentacija(prezentacijaId));
    }

    /** Ceo slajd, PLATNO bez poteza i sa brojem tačaka po crtežu, i link njegove prezentacije; nepostojeći je 404. */
    public McpSlajd slajd(Long id) {
        Long slajdId = obavezan(id, "slajdId");
        SlajdDetails s = prezentacijaService.slajd(slajdId);
        Long prezentacijaId = prezentacijaService.prezentacijaSlajda(slajdId);
        return new McpSlajd(PrezentacijaPregled.bezPoteza(s),
                s.platno() == null ? null : PlatnoPregled.brojTacaka(s.platno()), linkovi.prezentacija(prezentacijaId));
    }

    // ---------------------------------------------------------------- upis

    /** Klodova nova prezentacija (serija upisa, podrazumevana podešavanja); jedno pravljenje iz budžeta pre upisa. */
    public PrezentacijaInfo napraviPrezentaciju(CreatePrezentacijaCmd cmd, String serija) {
        Serija.proveri(serija);
        if (cmd == null) {
            throw new SystemException(McpGreske.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        budzet.trosiPravljenje(McpZahtev.oznaka(), 1);
        return info(prezentacijaService.kreiraj(cmd, serija));
    }

    /**
     * Klodov INFO ili PITANJE slajd sa serijom, na kraj ili odmah posle {@code posle}. Tip van {@link #DOZVOLJENI_TIPOVI}
     * je 400 {@value #SAMO_INFO_I_PITANJE} bez troška (bez tipa odlučuje {@code SlajdPP}); zatim jedno pravljenje iz budžeta,
     * pa servis pod ključem prezentacije (409 dok se izvodi, pa puna provera sadržaja i Markdown slika).
     */
    public McpSlajdSazetak dodajSlajd(Long prezentacijaId, Long posle, SlajdCmd cmd, String serija) {
        Serija.proveri(serija);
        Long id = obavezan(prezentacijaId, "prezentacijaId");
        if (cmd == null) {
            throw new SystemException(McpGreske.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        if (cmd.tip() != null && !DOZVOLJENI_TIPOVI.contains(cmd.tip())) {
            throw new SystemException(SAMO_INFO_I_PITANJE, HttpStatus.BAD_REQUEST);
        }
        budzet.trosiPravljenje(McpZahtev.oznaka(), 1);
        return PrezentacijaPregled.sazetak(prezentacijaService.dodajSlajdIzSerije(id, cmd, posle, serija));
    }

    // ---------------------------------------------------------------- pomoćno

    private static PrezentacijaInfo info(PrezentacijaDetails d) {
        return new PrezentacijaInfo(d.id(), d.naziv(), d.opis(), d.predmet(), d.brojSlajdova(), d.brojPitanja(),
                d.izmenjeno(), d.takmicenje(), d.telefonPrikaz(), d.detaljiDozvoljeni(), d.brojIzvodjenja(),
                d.aktivnoIzvodjenjeId(), d.tema());
    }

    /** Id iz argumenta alata; bez njega 400 {@code Neispravan parametar: <ime>.} (kao REST za pogrešan parametar). */
    private static Long obavezan(Long id, String ime) {
        if (id == null) {
            throw new SystemException("Neispravan parametar: " + ime + ".", HttpStatus.BAD_REQUEST);
        }
        return id;
    }
}
