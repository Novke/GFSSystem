package tri.novica.gfssystem.mcp.alati;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaInfo;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.uzivo.Tema;
import tri.novica.gfssystem.mcp.McpGreske;
import tri.novica.gfssystem.mcp.McpLinkovi;
import tri.novica.gfssystem.mcp.McpOdgovor;
import tri.novica.gfssystem.mcp.McpPatch;
import tri.novica.gfssystem.mcp.servis.McpPrezentacije;
import tri.novica.gfssystem.mcp.servis.McpSlajdSazetak;

/**
 * MCP alati prezentacija (spec MCP 4.4): čitanje ({@code prezentacije}, {@code prezentacija}, {@code slajd}) i pravljenje
 * ({@code napravi_prezentaciju}, {@code dodaj_slajd}; postojeći slajdovi se u A1 ne menjaju). Tanki su: telo je
 * {@link McpGreske#izvrsi} oko jedne metode fasade {@link McpPrezentacije}; upisni alat ide kroz varijantu sa serijom.
 * Argument {@code slajd} se čita strogo iz sirovih argumenata ({@link McpPatch#strogo}); tipiziran parametar postoji zbog
 * šeme alata, a nepoznat ključ i {@code null} za primitiv u njemu odbija već {@code McpOmotac}.
 */
@Component
public class PrezentacijaAlati {

    private final McpPrezentacije prezentacije;
    private final McpOdgovor odgovor;
    private final McpLinkovi linkovi;

    public PrezentacijaAlati(McpPrezentacije prezentacije, McpOdgovor odgovor, McpLinkovi linkovi) {
        this.prezentacije = prezentacije;
        this.odgovor = odgovor;
        this.linkovi = linkovi;
    }

    /** Rezultat {@code napravi_prezentaciju}: nova prezentacija i link u UI-ju. */
    public record PrezentacijaSaLinkom(PrezentacijaInfo prezentacija, String link) {
    }

    /** Rezultat {@code dodaj_slajd}: sažetak novog slajda (kao u {@code prezentacija}) i link prezentacije. */
    public record SlajdSaLinkom(McpSlajdSazetak slajd, String link) {
    }

    // ---------------------------------------------------------------- čitanje

    @McpTool(name = "prezentacije", description = "Prezentacije predmeta, najnovije izmenjene prve: id, naziv, opis, broj "
            + "slajdova i pitanja, vreme izmene, podešavanja, broj izvođenja, tema i `aktivnoIzvodjenjeId` (nije null dok se "
            + "prezentacija upravo izvodi: tada je ne menjaš). Sadržaj daje `prezentacija`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult prezentacije(@McpToolParam(description = "Id predmeta iz `kontekst`.") Long predmetId) {
        return McpGreske.izvrsi("prezentacije", poziv -> {
            poziv.id("predmet", predmetId);
            return odgovor.json(prezentacije.prezentacije(predmetId));
        });
    }

    @McpTool(name = "prezentacija", description = "Sažet pregled prezentacije po stranama (`strana`, `brojStrana`; strana ima "
            + "najviše 30 slajdova, a obična vežba staje u jednu): naziv, opis, tema, `aktivnoIzvodjenjeId` dok se izvodi, "
            + "`brojSlajdova`, `link` i po slajdu id, rb, tip, beleške (prvih 200 znakova), prelaz i `brojKoraka` (klikova do "
            + "kraja slajda). INFO: naslov i Markdown sadržaj (do 1 000 znakova); PITANJE: tip, tekst i opcije sa tačnim; "
            + "PLATNO: slojevi redom od najnižeg, sa id, tipom, nazivom, `okvir` `[x, y, sirina, visina]` na platnu 960 x 540, "
            + "tekstom (pasusi spojeni sa `\\n`, preovlađujući font i veličina), tabelom kao mrežom teksta, `latex` formule, "
            + "`medijId` slike i crteža i brojem tačaka crteža; ZADATAK: `zadatak` sa `grupaId`, `putanja` od korena banke "
            + "(poslednji element je sama grupa), `brojAktivnih`, `brojAktivnihPoNivou`, `brojVarijanti`, `nivo`, `vremeSekunde`, "
            + "isključenjima i "
            + "`upozorenja`. Skraćen tekst se završava sa `…`; ceo slajd daje `slajd`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult prezentacija(
            @McpToolParam(description = "Id prezentacije iz `prezentacije`.") Long prezentacijaId,
            @McpToolParam(description = "Strana od 1; podrazumevano 1.", required = false) Integer strana) {
        return McpGreske.izvrsi("prezentacija", poziv -> {
            poziv.id("prezentacija", prezentacijaId);
            return odgovor.json(prezentacije.prezentacija(prezentacijaId, strana == null ? 1 : strana));
        });
    }

    @McpTool(name = "slajd", description = "Jedan slajd ceo, kao u editoru: INFO naslov, Markdown sadržaj i slika; PITANJE "
            + "sa svim poljima i opcijama; PLATNO normalizovan dokument platna bez poteza crteža (`potezi` je null, a broj "
            + "tačaka po CRTEZ sloju je u `brojTacaka`). Plus beleške, prelaz i `link` prezentacije u UI-ju. Postojeće "
            + "slajdove u ovoj verziji samo čitaš.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult slajd(@McpToolParam(description = "Id slajda iz `prezentacija`.") Long slajdId) {
        return McpGreske.izvrsi("slajd", poziv -> {
            poziv.id("slajd", slajdId);
            return odgovor.json(prezentacije.slajd(slajdId));
        });
    }

    // ---------------------------------------------------------------- upis

    @McpTool(name = "napravi_prezentaciju", description = "Nova prazna prezentacija predmeta sa tvojom serijom i "
            + "podrazumevanim podešavanjima (bez takmičenja, telefon prikazuje dugmad); slajdove dodaješ sa `dodaj_slajd`. "
            + "Vraća `{prezentacija, link}`. " + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult napraviPrezentaciju(
            @McpToolParam(description = "Serija ovog zahteva, npr. `vezbe3-zadaci` (3 do 60 malih slova, cifara ili "
                    + "crtica).") String serija,
            @McpToolParam(description = "Id predmeta iz `kontekst`.") Long predmetId,
            @McpToolParam(description = "Naziv prezentacije, najviše 200 znakova.") String naziv,
            @McpToolParam(description = "Opis, najviše 1 000 znakova.", required = false) String opis,
            @McpToolParam(description = "Tema izgleda: `NACRT` (podrazumevano), `BELA`, `PAPIR`, `TAMNA` ili `PONOC`.",
                    required = false) Tema tema) {
        return McpGreske.izvrsi("napravi_prezentaciju", serija, poziv -> {
            poziv.id("predmet", predmetId);
            PrezentacijaInfo p = prezentacije.napraviPrezentaciju(new CreatePrezentacijaCmd(predmetId, naziv, opis, tema),
                    serija);
            poziv.id("prezentacija", p.id());
            return odgovor.json(new PrezentacijaSaLinkom(p, linkovi.prezentacija(p.id())));
        });
    }

    @McpTool(name = "dodaj_slajd", description = "Dodaje jedan `INFO`, `PITANJE` ili `ZADATAK` slajd sa tvojom serijom u "
            + "prezentaciju (i Novicinu): bez `posle` na kraj, sa `posle` odmah iza tog slajda. Oblik `slajd` i pravila su u "
            + "vodiču `pitanja`; drugi tip je greška `Klod u ovoj verziji dodaje samo INFO, PITANJE i ZADATAK slajdove.`, a "
            + "prezentacija koja se upravo izvodi se ne menja. ZADATAK: `{tip: \"ZADATAK\", zadatak: {grupaId, vremeSekunde?, "
            + "brojVarijanti?, nivo?}}`; grupa iz banke istog predmeta, namena PREDAVANJE; prazno `brojVarijanti` = sve aktivne, "
            + "`nivo` 1-3 = svi studenti dobijaju varijante tog nivoa, prazno = bez izbora: pri svakom otvaranju server izvlači jedan nivo, isti za sve (Novica oba menja i u konzoli); uvek zadaj nivo kad grupa ima varijante više nivoa. Pročitaj `slajd.zadatak.upozorenja` i javi ih Novici. Vraća `{slajd, link}` "
            + "(sažetak slajda kao u `prezentacija`). " + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult dodajSlajd(
            CallToolRequest zahtev,
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id prezentacije iz `prezentacije` ili `napravi_prezentaciju`.") Long prezentacijaId,
            @McpToolParam(description = "Id slajda iza kog se dodaje; bez njega na kraj.", required = false) Long posle,
            @McpToolParam(description = "Slajd: `tip` (`INFO`, `PITANJE` ili `ZADATAK`), `naslov`, `sadrzaj`, `slikaId`, "
                    + "`beleske`, `postepeno`, `pitanje`, `zadatak`, `prelaz` (vodič `pitanja`); `platno` se u ovoj verziji ne "
                    + "šalje.")
            SlajdCmd slajd) {
        return McpGreske.izvrsi("dodaj_slajd", serija, poziv -> {
            poziv.id("prezentacija", prezentacijaId);
            McpSlajdSazetak s = prezentacije.dodajSlajd(prezentacijaId, posle,
                    McpPatch.iz(zahtev).strogo("slajd", SlajdCmd.class), serija);
            poziv.id("slajd", s.id());
            return odgovor.json(new SlajdSaLinkom(s, linkovi.prezentacija(prezentacijaId)));
        });
    }
}
