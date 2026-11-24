package tri.novica.gfssystem.mcp.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.servis.McpBanka;
import tri.novica.gfssystem.mcp.servis.McpFajlovi;
import tri.novica.gfssystem.mcp.servis.McpMedij;
import tri.novica.gfssystem.mcp.servis.McpPrezentacije;
import tri.novica.gfssystem.utility.Serija;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Upload fajlova za Kloda (spec MCP 4.5): model ne prenosi fajl kroz argument alata (100 KB PNG je ~130 000 znakova
 * izlaza), nego ga pomoćna skripta {@code gfs-mcp-upload} šalje ovde kao multipart ({@code fajl}, {@code serija}), a Klod
 * dobija samo {@code medijId}. Pod istom putanjom kao {@code /mcp}, pa iza istog filtera ključa ({@code McpKljucFilter},
 * {@code /mcp/*}: bez ključeva 404, bez ispravnog ključa 401); nikad pod {@code /public}. Tip se prepoznaje po prvim
 * bajtovima (deklarisani MIME i ime se ne veruju), fajl je najviše 10 MB (granica multipart-a), svaki upload troši dnevni
 * budžet ključa, a serija van obrasca (i bez serije) je 400 pre čitanja fajla. Greške idu kroz postojeće handlere
 * ({@code {reason, time}}). Kontroler zove samo fasade, kao alati.
 */
@RestController
@RequestMapping("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}")
public class McpUploadRest {

    private final McpBanka banka;
    private final McpPrezentacije prezentacije;
    private final McpFajlovi fajlovi;

    public McpUploadRest(McpBanka banka, McpPrezentacije prezentacije, McpFajlovi fajlovi) {
        this.banka = banka;
        this.prezentacije = prezentacije;
        this.fajlovi = fajlovi;
    }

    /**
     * Po {@code namena}: {@code prilog} (podrazumevano) je prilog zadatka banke (PNG, JPEG, PDF) za {@code prilozi}
     * varijante; {@code fajl} je fajl za studente uz prezentaciju (DOCX, XLSX, PPTX, PDF, ZIP; vrsta
     * {@code FAJL_PREZENTACIJE}), pa ga {@code dodaj_fajl} veže za prezentaciju. Nijedan nije javan. Ista putanja, bez nove
     * MCP putanje; nepoznata namena je 400.
     */
    @PostMapping(path = "/prilozi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public McpMedij prilog(@RequestParam("fajl") MultipartFile fajl, @RequestParam(required = false) String serija,
                           @RequestParam(required = false) String namena) {
        String s = Serija.proveri(serija);
        if (namena == null || namena.equals("prilog")) {
            return banka.sacuvajPrilog(bajtovi(fajl), fajl.getOriginalFilename(), s);
        }
        if (namena.equals("fajl")) {
            return fajlovi.sacuvajFajl(bajtovi(fajl), fajl.getOriginalFilename(), s);
        }
        throw new SystemException("Nepoznata namena: dozvoljeno je `prilog` ili `fajl`.", HttpStatus.BAD_REQUEST);
    }

    /** Slika slajda ili pitanja (PNG, JPEG, GIF, WebP), vrsta {@code SLIKA}. */
    @PostMapping(path = "/slike", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public McpMedij slika(@RequestParam("fajl") MultipartFile fajl, @RequestParam(required = false) String serija) {
        String s = Serija.proveri(serija);
        return prezentacije.sacuvajSliku(bajtovi(fajl), fajl.getOriginalFilename(), s);
    }

    private static byte[] bajtovi(MultipartFile fajl) {
        try {
            return fajl.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
