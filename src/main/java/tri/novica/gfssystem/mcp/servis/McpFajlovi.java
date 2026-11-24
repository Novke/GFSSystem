package tri.novica.gfssystem.mcp.servis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.fajlovi.FajlPrezentacijeCmd;
import tri.novica.gfssystem.dto.fajlovi.FajlPrezentacijeInfo;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.McpBudzet;
import tri.novica.gfssystem.mcp.McpZahtev;
import tri.novica.gfssystem.service.fajlovi.FajloviPrezentacijeServis;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.utility.Serija;

import java.util.List;

/**
 * Fasada: fajlovi za studente uz prezentaciju za Kloda (spec fajlova 7; nivo "slobodno", bez kartice potvrde). Upload ide
 * kroz postojeći {@code POST /mcp/prilozi} sa {@code namena=fajl} ({@code McpUploadRest}), pa {@link #dodajFajl} veže medij
 * za prezentaciju. Pravila (granice, 409 za zauzet medij, slajd iz iste prezentacije) su u {@link FajloviPrezentacijeServis},
 * koji koristi i REST; prezentacija koja se izvodi SME da menja fajlove.
 */
@Service
@Slf4j
public class McpFajlovi {

    private final McpBudzet budzet;
    private final FajloviPrezentacijeServis fajlovi;
    private final MedijService medijService;

    public McpFajlovi(McpBudzet budzet, FajloviPrezentacijeServis fajlovi, MedijService medijService) {
        this.budzet = budzet;
        this.fajlovi = fajlovi;
        this.medijService = medijService;
    }

    /** Fajlovi prezentacije redom (rb, id). */
    public List<FajlPrezentacijeInfo> fajloviPrezentacije(Long prezentacijaId) {
        return fajlovi.lista(obavezan(prezentacijaId, "prezentacijaId"));
    }

    /** Veže otpremljeni medij vrste {@code FAJL_PREZENTACIJE} za prezentaciju (i opciono slajd), na kraj spiska. */
    public FajlPrezentacijeInfo dodajFajl(Long prezentacijaId, String medijId, Long slajdId, String naziv, String serija) {
        Serija.proveri(serija);
        if (medijId == null || medijId.isBlank()) {
            throw new SystemException("Neispravan parametar: medijId.", HttpStatus.BAD_REQUEST);
        }
        return fajlovi.dodajIzMedija(obavezan(prezentacijaId, "prezentacijaId"), medijId, slajdId, naziv, serija);
    }

    /** Novo ime i slajd ({@code null} = uz celu prezentaciju); oba se uvek šalju. */
    public FajlPrezentacijeInfo izmeniFajl(Long fajlId, String naziv, Long slajdId, String serija) {
        Serija.proveri(serija);
        return fajlovi.izmeni(obavezan(fajlId, "fajlId"), new FajlPrezentacijeCmd(naziv, slajdId));
    }

    /** Briše fajl i njegov medij. */
    public void obrisiFajl(Long fajlId, String serija) {
        Serija.proveri(serija);
        fajlovi.obrisi(obavezan(fajlId, "fajlId"));
    }

    /**
     * Fajl za studente ({@code POST /mcp/prilozi} sa {@code namena=fajl}): DOCX, XLSX, PPTX, PDF ili ZIP po prvim bajtovima i
     * ekstenziji imena, vrsta {@code FAJL_PREZENTACIJE}. Veličina se troši iz dnevnog budžeta upload-a pre upisa.
     */
    public McpMedij sacuvajFajl(byte[] sadrzaj, String naziv, String serija) {
        Serija.proveri(serija);
        String oznaka = McpZahtev.oznaka();
        budzet.trosiUpload(oznaka, sadrzaj == null ? 0 : sadrzaj.length);
        MedijInfo info = medijService.sacuvajFajl(sadrzaj, naziv);
        VrstaMedija vrsta = medijService.vrsta(info.id())
                .orElseThrow(() -> new IllegalStateException("Sačuvan fajl bez reda: " + info.id()));
        // nikad ime fajla ni sadržaj
        log.info("MCP upload=fajl kljuc={} serija={} id={} vrsta={} velicina={}", oznaka, serija, info.id(), vrsta,
                info.velicina());
        return new McpMedij(info.id(), vrsta, info.mime(), info.velicina());
    }

    private static Long obavezan(Long id, String ime) {
        if (id == null) {
            throw new SystemException("Neispravan parametar: " + ime + ".", HttpStatus.BAD_REQUEST);
        }
        return id;
    }
}
