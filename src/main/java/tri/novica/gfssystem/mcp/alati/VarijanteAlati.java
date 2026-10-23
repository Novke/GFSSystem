package tri.novica.gfssystem.mcp.alati;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.PrilogCmd;
import tri.novica.gfssystem.dto.banka.RenderProbaCmd;
import tri.novica.gfssystem.dto.banka.VarijantaCmd;
import tri.novica.gfssystem.dto.banka.VarijantaPatch;
import tri.novica.gfssystem.mcp.McpGreske;
import tri.novica.gfssystem.mcp.McpLinkovi;
import tri.novica.gfssystem.mcp.McpOdgovor;
import tri.novica.gfssystem.mcp.McpPatch;
import tri.novica.gfssystem.mcp.servis.McpBanka;
import tri.novica.gfssystem.mcp.servis.McpProba;
import tri.novica.gfssystem.mcp.servis.McpSaSlikom;
import tri.novica.gfssystem.mcp.servis.McpUpisVarijante;
import tri.novica.gfssystem.mcp.servis.McpVarijanta;
import tri.novica.gfssystem.mcp.servis.ZahtevSlike;

import java.util.List;
import java.util.Map;

/**
 * MCP alati varijanti banke (spec MCP 4.1, 4.3): {@code napravi_varijantu}, {@code izmeni_varijantu},
 * {@code dupliraj_varijantu}, {@code obrisi_varijantu} i {@code proba_rendera}. Tanki su: telo je {@link McpGreske#izvrsi}
 * oko jedne metode fasade {@link McpBanka}, a rezultat se samo serijalizuje ({@link McpOdgovor#jsonSaSlikom}). Odgovori,
 * prilozi i merge patch izmene se čitaju strogo iz sirovih argumenata ({@link McpPatch}); tipizirani parametri postoje
 * zbog šeme alata. Slika je podrazumevano uključena u upisu i probi.
 */
@Component
public class VarijanteAlati {

    private final McpBanka banka;
    private final McpOdgovor odgovor;
    private final McpLinkovi linkovi;

    public VarijanteAlati(McpBanka banka, McpOdgovor odgovor, McpLinkovi linkovi) {
        this.banka = banka;
        this.odgovor = odgovor;
        this.linkovi = linkovi;
    }

    /** Rezultat {@code dupliraj_varijantu}: duplikat i link u UI-ju. */
    public record VarijantaSaLinkom(McpVarijanta varijanta, String link) {
    }

    @McpTool(name = "napravi_varijantu", description = "Nova varijanta na kraj lista banke (grupa bez podgrupa, sa "
            + "`tezina`; i u Novicinoj grupi): uvek `NACRT` sa izvorom `CLAUDE` i tvojom serijom, odobrava je samo Novica. "
            + "`odgovori` pokrivaju svako polje šeme grupe, a `nivo` (1-3, obavezan) kaže koliko je varijanta teška unutar "
            + "svog tipa (vodič `banka`). Server renderuje odmah: greška rendera nije "
            + "neuspeh poziva nego `renderStatus: \"GRESKA\"` sa `renderGreska` (linija u tvom tekstu i poruka) i `renderDeo` "
            + "(`tekst` ili `resenje`); popravi je sa `izmeni_varijantu`. Uz `slika` vraća PNG: za namenu `PREDAVANJE` "
            + "zadatak i rešenje, za `TEST` samo zadatak, osim uz `slikaResenja: true`. Vraća `{radnja: \"nova\", varijanta, "
            + "link, linkPregleda}`. " + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult napraviVarijantu(
            CallToolRequest zahtev,
            @McpToolParam(description = "Serija ovog zahteva, npr. `uvoz-kol1-2024` (3 do 60 malih slova, cifara ili "
                    + "crtica).") String serija,
            @McpToolParam(description = "Id lista iz `stablo_banke` ili `grupa`.") Long grupaId,
            @McpToolParam(description = "Naziv varijante, npr. izvor zadatka; najviše 120 znakova.", required = false)
            String naziv,
            @McpToolParam(description = "LaTeX teksta zadatka (telo dokumenta za telefon, vodič `banka`).") String tekst,
            @McpToolParam(description = "LaTeX rešenja.", required = false) String resenje,
            @McpToolParam(description = "Tačan odgovor za svako polje šeme grupe: ključ polja -> tačno jedno svojstvo po "
                    + "tipu polja (`broj`, `prihvatljivi`, `tacna` ili `tacne`; vodič `banka`).")
            Map<String, OdgovorPolja> odgovori,
            @McpToolParam(description = "Nivo unutar tipa zadatka: 1 (lakša), 2 (srednja) ili 3 (teža varijanta); "
                    + "Novica ga potvrđuje pri pregledu.") Integer nivo,
            @McpToolParam(description = "Prilozi `[{ime, medijId}]` (medijId iz `gfs-mcp-upload`); LaTeX ih zove "
                    + "`\\includegraphics{ime}`.", required = false) List<PrilogCmd> prilozi,
            @McpToolParam(description = "Da li da vrati PNG render; podrazumevano true.", required = false) Boolean slika,
            @McpToolParam(description = "Za namenu `TEST`: da li slika uključuje i rešenje; podrazumevano false.",
                    required = false) Boolean slikaResenja) {
        return McpGreske.izvrsi("napravi_varijantu", serija, poziv -> {
            poziv.id("grupa", grupaId);
            McpPatch argumenti = McpPatch.iz(zahtev);
            McpSaSlikom<McpUpisVarijante> r = banka.napraviVarijantu(grupaId, new VarijantaCmd(naziv, tekst, resenje,
                    argumenti.odgovori("odgovori"), argumenti.prilozi("prilozi"), null,
                    argumenti.strogo("nivo", Integer.class)), serija,   // strogo: 2.5 i "2" su greška, ne 2
                    slika(slika, slikaResenja), poziv);
            poziv.id("varijanta", r.podaci().varijanta().id());
            return odgovor.jsonSaSlikom(r.podaci(), r.png(), r.napomena());
        });
    }

    @McpTool(name = "izmeni_varijantu", description = "Delimična izmena varijante: izostavljen ključ ostaje kakav jeste, "
            + "`null` briše `naziv`, `resenje` ili `prilozi` (`tekst` i `odgovori` se ne brišu); `odgovori` i `prilozi` "
            + "zamenjuju ceo skup, i bar jedno polje mora biti poslato. Tvoj neodobren nacrt (`NACRT`, izvor `CLAUDE`, "
            + "`ikadOdobrena: false`) se menja na mestu (`radnja: \"izmenjena\"`). Svaka druga varijanta (Novicina, aktivna, "
            + "arhivirana, ikad odobrena) se ne dira: izmena postaje nova kopija-nacrt koja original zamenjuje tek kad je "
            + "Novica odobri (`radnja: \"kopija\"`, `linkOriginala`); svaki takav poziv pravi novu kopiju, i kad poslate "
            + "vrednosti ne menjaju ništa, zato šalji samo polja koja stvarno menjaš. `nivo` (1-3) se ne briše; izmena samo "
            + "nivoa radi samo na tvom neodobrenom nacrtu (nivo ostalih menja Novica u banci). Render i slika kao u "
            + "`napravi_varijantu`. Vraća `{radnja, varijanta, link}`. " + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult izmeniVarijantu(
            CallToolRequest zahtev,
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id varijante.") Long varijantaId,
            @McpToolParam(description = "Nov naziv; `null` briše.", required = false) String naziv,
            @McpToolParam(description = "Nov LaTeX teksta zadatka.", required = false) String tekst,
            @McpToolParam(description = "Nov LaTeX rešenja; `null` briše.", required = false) String resenje,
            @McpToolParam(description = "Novi odgovori za svako polje šeme grupe (cela mapa).", required = false)
            Map<String, OdgovorPolja> odgovori,
            @McpToolParam(description = "Nov ceo spisak priloga `[{ime, medijId}]`; `null` briše sve.", required = false)
            List<PrilogCmd> prilozi,
            @McpToolParam(description = "Nov nivo: 1 (lakša), 2 (srednja) ili 3 (teža varijanta).", required = false)
            Integer nivo,
            @McpToolParam(description = "Da li da vrati PNG render; podrazumevano true.", required = false) Boolean slika,
            @McpToolParam(description = "Za namenu `TEST`: da li slika uključuje i rešenje; podrazumevano false.",
                    required = false) Boolean slikaResenja) {
        return McpGreske.izvrsi("izmeni_varijantu", serija, poziv -> {
            poziv.id("varijanta", varijantaId);
            McpPatch patch = McpPatch.iz(zahtev);
            VarijantaPatch izmena = new VarijantaPatch(patch.promena("naziv", String.class, true),
                    patch.promena("tekst", String.class, false), patch.promena("resenje", String.class, true),
                    patch.promena("odgovori", false, patch::odgovori), patch.promena("prilozi", true, patch::prilozi),
                    patch.promena("nivo", Integer.class, false));
            McpSaSlikom<McpUpisVarijante> r = banka.izmeniVarijantu(varijantaId, izmena, serija,
                    slika(slika, slikaResenja), poziv);
            if ("kopija".equals(r.podaci().radnja())) {
                poziv.id("kopija", r.podaci().varijanta().id());
            }
            return odgovor.jsonSaSlikom(r.podaci(), r.png(), r.napomena());
        });
    }

    @McpTool(name = "dupliraj_varijantu", description = "Kopija bilo koje varijante (i Novicine) na kraj iste grupe kao "
            + "tvoj nacrt iz serije (`NACRT`, izvor `CLAUDE`): isti naziv, tekst, rešenje, odgovori i prilozi, sopstveni "
            + "render. Polazna tačka za sličnu varijantu, koju zatim menjaš sa `izmeni_varijantu`. Vraća "
            + "`{varijanta, link}`, bez slike. " + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult duplirajVarijantu(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id varijante koja se duplira.") Long varijantaId) {
        return McpGreske.izvrsi("dupliraj_varijantu", serija, poziv -> {
            poziv.id("varijanta", varijantaId);
            McpVarijanta duplikat = banka.duplirajVarijantu(varijantaId, serija);
            poziv.id("duplikat", duplikat.id());
            return odgovor.json(new VarijantaSaLinkom(duplikat, linkovi.varijanta(duplikat.id())));
        });
    }

    @McpTool(name = "obrisi_varijantu", description = "Briše tvoj neodobren nacrt (`NACRT`, izvor `CLAUDE`, "
            + "`ikadOdobrena: false`); svaka druga varijanta je greška `Klod briše samo svoje neodobrene nacrte.` Vraća "
            + "`{obrisano: true}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false),
            metaProvider = TraziPotvrdu.class)
    public CallToolResult obrisiVarijantu(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id tvog neodobrenog nacrta.") Long varijantaId) {
        return McpGreske.izvrsi("obrisi_varijantu", serija, poziv -> {
            poziv.id("varijanta", varijantaId);
            banka.obrisiVarijantu(varijantaId, serija);
            return odgovor.json(Map.of("obrisano", true));
        });
    }

    @McpTool(name = "proba_rendera", description = "Render LaTeX-a bez čuvanja, sa preambulom predmeta i istim pravilima "
            + "kao upis varijante (zabranjene komande, prilozi). Vraća `{ok, greska, deo}` (`greska` sa linijom u delu "
            + "`tekst` ili `resenje`) i, uz `slika`, PNG zadatka i poslatog rešenja. Ništa se ne upisuje. Najviše 3 probe "
            + "istovremeno po serveru (inače `Previše proba u isto vreme.`), zato ih šalji jednu po jednu.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult probaRendera(
            CallToolRequest zahtev,
            @McpToolParam(description = "Id predmeta iz `kontekst` (njegova preambula).") Long predmetId,
            @McpToolParam(description = "LaTeX teksta zadatka.") String tekst,
            @McpToolParam(description = "LaTeX rešenja.", required = false) String resenje,
            @McpToolParam(description = "Prilozi `[{ime, medijId}]`.", required = false) List<PrilogCmd> prilozi,
            @McpToolParam(description = "Da li da vrati PNG render; podrazumevano true.", required = false) Boolean slika) {
        return McpGreske.izvrsi("proba_rendera", poziv -> {
            poziv.id("predmet", predmetId);
            McpSaSlikom<McpProba> r = banka.probaRendera(new RenderProbaCmd(predmetId, tekst, resenje,
                    McpPatch.iz(zahtev).prilozi("prilozi")), new ZahtevSlike(slika == null || slika, true));
            return odgovor.jsonSaSlikom(r.podaci(), r.png(), r.napomena());
        });
    }

    /** Slika upisa: podrazumevano uključena, rešenje za namenu TEST samo uz {@code slikaResenja}. */
    private static ZahtevSlike slika(Boolean slika, Boolean slikaResenja) {
        return new ZahtevSlike(slika == null || slika, Boolean.TRUE.equals(slikaResenja));
    }
}
