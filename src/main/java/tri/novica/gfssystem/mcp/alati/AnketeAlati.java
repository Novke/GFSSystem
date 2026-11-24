package tri.novica.gfssystem.mcp.alati;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.anketa.PokreniAnketuCmd;
import tri.novica.gfssystem.dto.anketa.SablonAnketeCmd;
import tri.novica.gfssystem.entity.anketa.BojaPrecice;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;
import tri.novica.gfssystem.mcp.McpGreske;
import tri.novica.gfssystem.mcp.McpOdgovor;
import tri.novica.gfssystem.mcp.servis.McpAnkete;

import java.util.List;
import java.util.Map;

/**
 * MCP alati brze ankete (spec brze ankete 8): {@code ankete_sabloni}, {@code sacuvaj_sablon_ankete},
 * {@code obrisi_sablon_ankete}, {@code sacuvaj_folder_ankete}, {@code obrisi_folder_ankete}, {@code poredjaj_precice},
 * {@code ankete_istorija}, {@code pokreni_anketu}, {@code zatvori_anketu} i {@code prikazi_rezultat_ankete}. Nivo
 * "slobodno": Novicina jasna molba je dovoljna, bez kartice potvrde. Tanki su: telo je {@link McpGreske#izvrsi} oko jedne
 * metode fasade {@link McpAnkete}. Nijedan ne vraća ko je za šta glasao.
 */
@Component
public class AnketeAlati {

    private final McpAnkete ankete;
    private final McpOdgovor odgovor;

    public AnketeAlati(McpAnkete ankete, McpOdgovor odgovor) {
        this.ankete = ankete;
        this.odgovor = odgovor;
    }

    @McpTool(name = "ankete_sabloni", description = "Sačuvana pitanja brze ankete: `folderi` (`id`, `naziv`, `rb`, `brojSablona`) i "
            + "`sabloni` (prečice prve, po `precicaRb`; `folderId` null = bez foldera). Pravila su u vodiču `ankete`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult anketeSabloni() {
        return McpGreske.izvrsi("ankete_sabloni", poziv -> odgovor.json(ankete.sabloni()));
    }

    @McpTool(name = "sacuvaj_sablon_ankete", description = "Čuva sačuvano pitanje (šablon) ankete, celo: sa `id` zamenjuje to "
            + "pitanje, bez `id` menja pitanje istog teksta ili pravi novo. `folder` je naziv foldera (pravi se kad ga nema; "
            + "bez njega pitanje je bez foldera). 2 do 10 odgovora. `trajanjeSekunde` važi za `NA_CASU`, `rokSati` za `GRUPA`. "
            + "`precica` ga stavlja na kraj traka prečica (najviše 12). Vraća sačuvan šablon.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult sacuvajSablonAnkete(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Pitanje, 1 do 300 znakova.") String pitanje,
            @McpToolParam(description = "Odgovori, 2 do 10, svaki 1 do 120 znakova, različiti.") List<String> opcije,
            @McpToolParam(description = "Id šablona koji se menja; bez njega novi (ili isti tekst pitanja).", required = false)
            Long id,
            @McpToolParam(description = "Naziv foldera; bez njega bez foldera.", required = false) String folder,
            @McpToolParam(description = "Više odgovora odjednom; podrazumevano false.", required = false) Boolean viseOdgovora,
            @McpToolParam(description = "`NA_CASU` (podrazumevano) ili `GRUPA`.", required = false) PublikaAnkete publika,
            @McpToolParam(description = "Za `NA_CASU`: trajanje 10 do 1800 s; bez njega dok se ne zatvori.", required = false)
            Integer trajanjeSekunde,
            @McpToolParam(description = "Za `GRUPA`: podrazumevani rok u satima, 1 do 336.", required = false) Integer rokSati,
            @McpToolParam(description = "Prečica u traci; podrazumevano false.", required = false) Boolean precica,
            @McpToolParam(description = "Ikonica prečice (emoji), do 16 znakova.", required = false) String ikonica,
            @McpToolParam(description = "Boja prečice: `PLAVA`, `ZELENA`, `LJUBICASTA`, `VISNJA`, `TIRKIZNA` ili `OKER`.",
                    required = false) BojaPrecice boja,
            @McpToolParam(description = "Kratak naziv prečice, do 30 znakova.", required = false) String kratakNaziv,
            @McpToolParam(description = "Prečica pokreće bez koraka izmene; podrazumevano false.", required = false)
            Boolean pokreniOdmah) {
        return McpGreske.izvrsi("sacuvaj_sablon_ankete", serija, poziv -> {
            poziv.id("sablon", id);
            SablonAnketeCmd cmd = new SablonAnketeCmd(null, pitanje, opcije, viseOdgovora, publika, trajanjeSekunde, rokSati,
                    precica, ikonica, boja, kratakNaziv, pokreniOdmah);
            return odgovor.json(ankete.sacuvajSablon(id, folder, cmd, serija));
        });
    }

    @McpTool(name = "obrisi_sablon_ankete", description = "Briše sačuvano pitanje (i prečicu, ako je bilo prečica). Ankete pokrenute "
            + "iz njega ostaju u istoriji. Vraća `{obrisan: id}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult obrisiSablonAnkete(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id šablona.") Long id) {
        return McpGreske.izvrsi("obrisi_sablon_ankete", serija, poziv -> {
            poziv.id("sablon", id);
            ankete.obrisiSablon(id, serija);
            return odgovor.json(Map.of("obrisan", id));
        });
    }

    @McpTool(name = "sacuvaj_folder_ankete", description = "Folder sačuvanih pitanja: sa `id` ga preimenuje, bez `id` vraća folder "
            + "tog naziva (bez obzira na velika i mala slova) ili pravi nov na kraju. Naziv 1 do 60 znakova. Vraća folder.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult sacuvajFolderAnkete(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Naziv foldera.") String naziv,
            @McpToolParam(description = "Id foldera koji se preimenuje.", required = false) Long id) {
        return McpGreske.izvrsi("sacuvaj_folder_ankete", serija, poziv -> {
            poziv.id("folder", id);
            return odgovor.json(ankete.sacuvajFolder(id, naziv, serija));
        });
    }

    @McpTool(name = "obrisi_folder_ankete", description = "Briše folder; njegova pitanja ostaju, bez foldera. Vraća `{obrisan: id}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult obrisiFolderAnkete(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id foldera.") Long id) {
        return McpGreske.izvrsi("obrisi_folder_ankete", serija, poziv -> {
            poziv.id("folder", id);
            ankete.obrisiFolder(id, serija);
            return odgovor.json(Map.of("obrisan", id));
        });
    }

    @McpTool(name = "poredjaj_precice", description = "Postavlja trake prečica: tačno ovi šabloni su prečice, ovim redom (najviše "
            + "12); ostali prestaju da budu prečice. Prazan spisak uklanja sve prečice. Vraća isto što i `ankete_sabloni`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult poredjajPrecice(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id-jevi šablona, redom traka.") List<Long> ids) {
        return McpGreske.izvrsi("poredjaj_precice", serija, poziv -> odgovor.json(ankete.poredjajPrecice(ids, serija)));
    }

    @McpTool(name = "ankete_istorija", description = "Pokrenute ankete, najnovije prve, najviše 50, po filteru (bez filtera sve): "
            + "pitanje, odgovori, `glasovi` (broj po odgovoru, istim redom), `brojGlasalo`, `brojMogucih`, `otvorena`, "
            + "`zatvoreno`, `rezultatPrikazan`, `izvor`. Ko je za šta glasao se ne vidi nigde.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult anketeIstorija(
            @McpToolParam(description = "Id predmeta: ankete pokrenute sa predavanja tog predmeta.", required = false)
            Long predmetId,
            @McpToolParam(description = "Id grupe.", required = false) Long grupaId,
            @McpToolParam(description = "Id predavanja.", required = false) Long predavanjeId) {
        return McpGreske.izvrsi("ankete_istorija", poziv -> {
            poziv.id("predmet", predmetId);
            poziv.id("grupa", grupaId);
            poziv.id("predavanje", predavanjeId);
            return odgovor.json(ankete.istorija(predmetId, grupaId, predavanjeId));
        });
    }

    @McpTool(name = "pokreni_anketu", description = "Pokreće anketu, samo na Novicinu jasnu molbu (\"pokreni anketu ...\"). Sa "
            + "`sablonId` ono što ne pošalješ dolazi iz sačuvanog pitanja. `NA_CASU` (podrazumevano): studenti čekirani na "
            + "predavanju; bez `predavanjeId` ide na današnje predavanje u toku kad je ono tačno jedno, i zatvara prethodnu "
            + "otvorenu anketu tog predavanja. `GRUPA`: cela grupa (`grupaId` ili grupa predavanja) do `rok` ili `rokSati` od "
            + "sada, najviše 14 dana. Vraća anketu sa `id`. Posle isteka vremena pogledaj `ankete_istorija`. "
            + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult pokreniAnketu(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Pitanje; bez njega iz šablona.", required = false) String pitanje,
            @McpToolParam(description = "Odgovori, 2 do 10; bez njih iz šablona.", required = false) List<String> opcije,
            @McpToolParam(description = "Id sačuvanog pitanja.", required = false) Long sablonId,
            @McpToolParam(description = "`NA_CASU` ili `GRUPA`; bez nje iz šablona, inače `NA_CASU`.", required = false)
            PublikaAnkete publika,
            @McpToolParam(description = "Više odgovora odjednom.", required = false) Boolean viseOdgovora,
            @McpToolParam(description = "Id predavanja.", required = false) Long predavanjeId,
            @McpToolParam(description = "Za `GRUPA`: id grupe.", required = false) Long grupaId,
            @McpToolParam(description = "Za `NA_CASU`: trajanje 10 do 1800 s; bez njega dok se ne zatvori.", required = false)
            Integer trajanjeSekunde,
            @McpToolParam(description = "Za `GRUPA`: rok kao lokalno vreme, npr. `2026-11-25T20:00`.", required = false)
            String rok,
            @McpToolParam(description = "Za `GRUPA` bez `rok`: rok za toliko sati od sada, 1 do 336.", required = false)
            Integer rokSati) {
        return McpGreske.izvrsi("pokreni_anketu", serija, poziv -> {
            poziv.id("sablon", sablonId);
            poziv.id("predavanje", predavanjeId);
            poziv.id("grupa", grupaId);
            PokreniAnketuCmd cmd = new PokreniAnketuCmd(sablonId, pitanje, opcije, viseOdgovora, publika, predavanjeId,
                    grupaId, trajanjeSekunde, null);
            return odgovor.json(ankete.pokreni(cmd, rok, rokSati, serija));
        });
    }

    @McpTool(name = "zatvori_anketu", description = "Zatvara anketu: posle toga niko ne glasa (ne otvara se ponovo). Već zatvorena "
            + "ostaje kakva jeste. Vraća anketu sa brojevima.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult zatvoriAnketu(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id ankete.") Long id) {
        return McpGreske.izvrsi("zatvori_anketu", serija, poziv -> {
            poziv.id("anketa", id);
            return odgovor.json(ankete.zatvori(id, serija));
        });
    }

    @McpTool(name = "prikazi_rezultat_ankete", description = "Pokazuje rezultat ankete studentima i na projektoru (`prikazan` true, "
            + "podrazumevano) ili ga skriva (`false`). Sme i dok je anketa otvorena. Vraća anketu sa brojevima.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult prikaziRezultatAnkete(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id ankete.") Long id,
            @McpToolParam(description = "true pokazuje, false skriva; podrazumevano true.", required = false) Boolean prikazan) {
        return McpGreske.izvrsi("prikazi_rezultat_ankete", serija, poziv -> {
            poziv.id("anketa", id);
            return odgovor.json(ankete.prikaziRezultat(id, prikazan, serija));
        });
    }
}
