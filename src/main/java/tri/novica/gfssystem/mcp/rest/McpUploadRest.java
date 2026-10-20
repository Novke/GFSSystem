package tri.novica.gfssystem.mcp.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tri.novica.gfssystem.mcp.servis.McpBanka;
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

    public McpUploadRest(McpBanka banka, McpPrezentacije prezentacije) {
        this.banka = banka;
        this.prezentacije = prezentacije;
    }

    /** Prilog zadatka banke (PNG, JPEG, PDF) za {@code prilozi} varijante; nije javan. */
    @PostMapping(path = "/prilozi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public McpMedij prilog(@RequestParam("fajl") MultipartFile fajl, @RequestParam(required = false) String serija) {
        String s = Serija.proveri(serija);
        return banka.sacuvajPrilog(bajtovi(fajl), fajl.getOriginalFilename(), s);
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
