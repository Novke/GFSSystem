package tri.novica.gfssystem.mcp.servis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaDetails;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaInfo;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.McpBudzet;
import tri.novica.gfssystem.mcp.McpGreske;
import tri.novica.gfssystem.mcp.McpLinkovi;
import tri.novica.gfssystem.mcp.McpZahtev;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.utility.Serija;
import tri.novica.gfssystem.utility.Vezba;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Fasada: Prezentacije za Kloda (spec MCP 4.4): pregled, slajd, pravljenje prezentacije i INFO/PITANJE
 * slajda. Jedino mesto gde MCP dodiruje postojeće servise; zove samo metode sa
 * {@code McpOpsegTest.DOZVOLJENE_METODE}, a skup njenih javnih metoda je zakucan u {@code McpFasadeTest.METODE}.
 * <p>
 * Čitanje je sažeto ({@link PrezentacijaPregled}: strane koje staju u jedan odgovor) ili pun slajd bez poteza crteža
 * ({@link McpSlajd}). Klod pravi prezentacije i dodaje slajdove sa serijom; samo tipove iz {@link #DOZVOLJENI_TIPOVI}
 * (INFO, PITANJE i ZADATAK; A2 dodaje PLATNO), a postojeće slajdove ne menja. Pravljenje troši jedno pravljenje iz dnevnog budžeta ključa
 * pre upisa; prezentacija koja se upravo izvodi se ne menja ({@code PrezentacijaService.dodajSlajdIzSerije}, 409).
 * <p>
 * Slike za slajdove (spec MCP 4.5) stižu kroz {@code POST /mcp/slike} ({@code McpUploadRest}), ne kroz argument alata;
 * {@link #sacuvajSliku} troši dnevni budžet upload-a pre upisa i vraća {@code medijId} vrste {@code SLIKA}.
 */
@Service
@Slf4j
public class McpPrezentacije {

    /** Tipovi slajda koje Klod dodaje kroz {@code dodaj_slajd}. */
    static final Set<TipSlajda> DOZVOLJENI_TIPOVI = EnumSet.of(TipSlajda.INFO, TipSlajda.PITANJE, TipSlajda.ZADATAK);
    /** 400 za {@code dodaj_slajd} sa tipom van {@link #DOZVOLJENI_TIPOVI}. */
    static final String SAMO_DOZVOLJENI = "Klod u ovoj verziji dodaje samo INFO, PITANJE i ZADATAK slajdove.";
    private static final String PREDMET_OBAVEZAN = "Predmet je obavezan.";

    private final McpLinkovi linkovi;
    private final McpBudzet budzet;
    private final PrezentacijaService prezentacijaService;
    private final MedijService medijService;

    public McpPrezentacije(McpLinkovi linkovi, McpBudzet budzet, PrezentacijaService prezentacijaService,
                           MedijService medijService) {
        this.linkovi = linkovi;
        this.budzet = budzet;
        this.prezentacijaService = prezentacijaService;
        this.medijService = medijService;
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

    /**
     * Klodova nova prezentacija (serija upisa, podrazumevana podešavanja); jedno pravljenje iz budžeta pre upisa. Bez
     * {@code vezba} broj se uzima iz naziva ({@link Vezba#izNaziva}).
     */
    public PrezentacijaInfo napraviPrezentaciju(CreatePrezentacijaCmd cmd, String serija) {
        Serija.proveri(serija);
        if (cmd == null) {
            throw new SystemException(McpGreske.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        if (cmd.vezba() == null) {
            // Klod najčešće ne navede broj; naziv "Vežbe N ..." ga daje kao i popuna u V19
            cmd = new CreatePrezentacijaCmd(cmd.predmetId(), cmd.naziv(), cmd.opis(), cmd.tema(),
                    Vezba.izNaziva(cmd.naziv()));
        }
        budzet.trosiPravljenje(McpZahtev.oznaka(), 1);
        return info(prezentacijaService.kreiraj(cmd, serija));
    }

    /**
     * Klodov INFO, PITANJE ili ZADATAK slajd sa serijom, na kraj ili odmah posle {@code posle}. Tip van {@link #DOZVOLJENI_TIPOVI}
     * je 400 {@value #SAMO_DOZVOLJENI} bez troška (bez tipa odlučuje {@code SlajdPP}); zatim jedno pravljenje iz budžeta,
     * pa servis pod ključem prezentacije (409 dok se izvodi, pa puna provera sadržaja i Markdown slika).
     */
    public McpSlajdSazetak dodajSlajd(Long prezentacijaId, Long posle, SlajdCmd cmd, String serija) {
        Serija.proveri(serija);
        Long id = obavezan(prezentacijaId, "prezentacijaId");
        if (cmd == null) {
            throw new SystemException(McpGreske.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        if (cmd.tip() != null && !DOZVOLJENI_TIPOVI.contains(cmd.tip())) {
            throw new SystemException(SAMO_DOZVOLJENI, HttpStatus.BAD_REQUEST);
        }
        budzet.trosiPravljenje(McpZahtev.oznaka(), 1);
        return PrezentacijaPregled.sazetak(prezentacijaService.dodajSlajdIzSerije(id, cmd, posle, serija));
    }

    /**
     * Slika za slajd ili pitanje ({@code POST /mcp/slike}): PNG, JPEG, GIF ili WebP po prvim bajtovima, vrsta {@code SLIKA}
     * (deklarisani MIME i ime se ne veruju; ostalo, i SVG i PDF, je 400 {@code Dozvoljene su samo slike PNG, JPEG, GIF i
     * WebP.}). Veličina se troši iz dnevnog budžeta upload-a ključa pre upisa (preko granice 429 i ništa se ne upisuje).
     * Vrsta se čita iz sačuvanog reda; jedan INFO red: oznaka ključa, serija, id, vrsta, veličina.
     */
    public McpMedij sacuvajSliku(byte[] sadrzaj, String naziv, String serija) {
        Serija.proveri(serija);
        String oznaka = McpZahtev.oznaka();
        budzet.trosiUpload(oznaka, sadrzaj == null ? 0 : sadrzaj.length);
        MedijInfo info = medijService.sacuvaj(sadrzaj, naziv);
        VrstaMedija vrsta = medijService.vrsta(info.id())
                .orElseThrow(() -> new IllegalStateException("Sačuvana slika bez reda: " + info.id()));
        // nikad ime fajla ni sadržaj
        log.info("MCP upload=slika kljuc={} serija={} id={} vrsta={} velicina={}", oznaka, serija, info.id(), vrsta,
                info.velicina());
        return new McpMedij(info.id(), vrsta, info.mime(), info.velicina());
    }

    // ---------------------------------------------------------------- pomoćno

    private static PrezentacijaInfo info(PrezentacijaDetails d) {
        return new PrezentacijaInfo(d.id(), d.naziv(), d.opis(), d.predmet(), d.brojSlajdova(), d.brojPitanja(),
                d.izmenjeno(), d.takmicenje(), d.telefonPrikaz(), d.detaljiDozvoljeni(), d.brojIzvodjenja(),
                d.aktivnoIzvodjenjeId(), d.tema(), d.vezba(), d.vrsta(), d.brojZadataka(), d.kreirano(),
                d.poslednjePustanje());
    }

    /** Id iz argumenta alata; bez njega 400 {@code Neispravan parametar: <ime>.} (kao REST za pogrešan parametar). */
    private static Long obavezan(Long id, String ime) {
        if (id == null) {
            throw new SystemException("Neispravan parametar: " + ime + ".", HttpStatus.BAD_REQUEST);
        }
        return id;
    }
}
