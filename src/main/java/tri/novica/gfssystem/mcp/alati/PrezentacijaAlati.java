package tri.novica.gfssystem.mcp.alati;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.PitanjeCmd;
import tri.novica.gfssystem.dto.uzivo.Prelaz;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaInfo;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaPatch;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdPatch;
import tri.novica.gfssystem.dto.uzivo.ZadatakSlajda;
import tri.novica.gfssystem.entity.uzivo.Tema;
import tri.novica.gfssystem.mcp.McpGreske;
import tri.novica.gfssystem.mcp.McpLinkovi;
import tri.novica.gfssystem.mcp.McpOdgovor;
import tri.novica.gfssystem.mcp.McpPatch;
import tri.novica.gfssystem.mcp.servis.McpPrezentacije;
import tri.novica.gfssystem.mcp.servis.McpSlajdSazetak;
import tri.novica.gfssystem.utility.Promena;

import java.util.Map;

/**
 * MCP alati prezentacija (spec MCP 4.4): čitanje ({@code prezentacije}, {@code prezentacija}, {@code slajd}) i pravljenje
 * ({@code napravi_prezentaciju}, {@code dodaj_slajd}), povratne izmene postojećih slajdova ({@code izmeni_slajd},
 * {@code zameni_platno}, {@code premesti_slajd}, {@code obrisi_slajd}, {@code istorija_slajda}, {@code vrati_izmenu}) i izmena
 * i brisanje prezentacije. Tanki su: telo je
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
            + "tačaka po CRTEZ sloju je u `brojTacaka`). Plus beleške, prelaz, `link` prezentacije u UI-ju i `otisak` za "
            + "`zameni_platno`.",
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
            + "`vezba` je redni broj vežbe (0-99; za \"Vežbe 3 i 4 – zadaci\" prvi, 3); bez njega broj se uzima iz naziva "
            + "koji počinje sa \"Vežbe N\", inače ostaje bez broja. Vraća `{prezentacija, link}`. " + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult napraviPrezentaciju(
            @McpToolParam(description = "Serija ovog zahteva, npr. `vezbe3-zadaci` (3 do 60 malih slova, cifara ili "
                    + "crtica).") String serija,
            @McpToolParam(description = "Id predmeta iz `kontekst`.") Long predmetId,
            @McpToolParam(description = "Naziv prezentacije, najviše 200 znakova.") String naziv,
            @McpToolParam(description = "Opis, najviše 1 000 znakova.", required = false) String opis,
            @McpToolParam(description = "Tema izgleda: `NACRT` (podrazumevano), `BELA`, `PAPIR`, `TAMNA` ili `PONOC`.",
                    required = false) Tema tema,
            @McpToolParam(description = "Redni broj vežbe 0-99 (redosled i predlog sledeće prezentacije).",
                    required = false) Integer vezba) {
        return McpGreske.izvrsi("napravi_prezentaciju", serija, poziv -> {
            poziv.id("predmet", predmetId);
            PrezentacijaInfo p = prezentacije.napraviPrezentaciju(
                    new CreatePrezentacijaCmd(predmetId, naziv, opis, tema, vezba), serija);
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

    // ---------------------------------------------------------------- izmene postojećeg

    @McpTool(name = "izmeni_prezentaciju", description = "Delimična izmena prezentacije (i Novicine): `naziv`, `opis`, `tema` "
            + "(`NACRT`, `BELA`, `PAPIR`, `TAMNA`, `PONOC`) i `vezba` (0-99); izostavljeno ostaje, `null` briše `opis` i `vezba`. "
            + "Podešavanja izvođenja menja Novica. Prezentacija koja se upravo izvodi se ne menja. Vraća `{prezentacija, link}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult izmeniPrezentaciju(
            CallToolRequest zahtev,
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id prezentacije.") Long prezentacijaId,
            @McpToolParam(description = "Nov naziv, najviše 200 znakova.", required = false) String naziv,
            @McpToolParam(description = "Nov opis, najviše 1 000 znakova; `null` briše.", required = false) String opis,
            @McpToolParam(description = "Nova tema izgleda.", required = false) Tema tema,
            @McpToolParam(description = "Redni broj vežbe 0-99; `null` briše.", required = false) Integer vezba) {
        return McpGreske.izvrsi("izmeni_prezentaciju", serija, poziv -> {
            poziv.id("prezentacija", prezentacijaId);
            McpPatch patch = McpPatch.iz(zahtev);
            PrezentacijaInfo p = prezentacije.izmeniPrezentaciju(prezentacijaId, new PrezentacijaPatch(
                    patch.promena("naziv", String.class, false), patch.promena("opis", String.class, true),
                    patch.promena("tema", Tema.class, false), patch.promena("vezba", Integer.class, true)), serija);
            return odgovor.json(new PrezentacijaSaLinkom(p, linkovi.prezentacija(p.id())));
        });
    }

    @McpTool(name = "obrisi_prezentaciju", description = "Briše prezentaciju sa svim slajdovima, izvođenjima i odgovorima studenata, "
            + "samo kad Novica to izričito traži i uz njegovu potvrdu: nema istorije ni vraćanja, a spas je samo backup. Pre poziva mu "
            + "reci koliko izvođenja ima (`brojIzvodjenja` iz `prezentacije`); `ocekivanoIzvodjenja` mora biti tačno taj broj. Ne radi "
            + "dok se prezentacija izvodi. Vraća `{obrisano: true}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false),
            metaProvider = TraziPotvrdu.class)
    public CallToolResult obrisiPrezentaciju(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id prezentacije.") Long prezentacijaId,
            @McpToolParam(description = "Tačan broj izvođenja prezentacije.") Long ocekivanoIzvodjenja) {
        return McpGreske.izvrsi("obrisi_prezentaciju", serija, poziv -> {
            poziv.id("prezentacija", prezentacijaId);
            prezentacije.obrisiPrezentaciju(prezentacijaId, ocekivanoIzvodjenja, serija);
            return odgovor.json(Map.of("obrisano", true));
        });
    }

    @McpTool(name = "izmeni_slajd", description = "Delimična izmena postojećeg slajda (i Novicinog), povratna: izostavljeno ostaje, "
            + "`null` briše. Po tipu: INFO `naslov`, `sadrzaj`, `slikaId`, `postepeno`; PITANJE `pitanje` (ceo objekat); ZADATAK "
            + "`zadatak` (ceo objekat); svi tipovi `beleske` i `prelaz`. PLATNO sadržaj ide kroz `zameni_platno`. `opis` kaže šta si "
            + "promenio. Prezentacija koja se upravo izvodi se ne menja. Vraća `{slajd, izmenaId, upozorenja, link}`; `vrati_izmenu` "
            + "sa `izmenaId` vraća prethodno stanje, a `izmenaId` je null kad se ništa nije promenilo.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult izmeniSlajd(
            CallToolRequest zahtev,
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id slajda.") Long slajdId,
            @McpToolParam(description = "Šta se menja, 1 do 300 znakova.") String opis,
            @McpToolParam(description = "INFO: naslov; `null` briše.", required = false) String naslov,
            @McpToolParam(description = "INFO: Markdown sadržaj; `null` briše.", required = false) String sadrzaj,
            @McpToolParam(description = "INFO: id slike iz `gfs-mcp-upload --slika`; `null` briše.", required = false) String slikaId,
            @McpToolParam(description = "Beleške za konzolu; `null` briše.", required = false) String beleske,
            @McpToolParam(description = "INFO: otkrivanje liste stavku po stavku.", required = false) Boolean postepeno,
            @McpToolParam(description = "Prelaz `{efekat, smer, trajanjeMs}`; `null` briše.", required = false) Prelaz prelaz,
            @McpToolParam(description = "PITANJE: ceo objekat pitanja (vodič `pitanja`).", required = false) PitanjeCmd pitanje,
            @McpToolParam(description = "ZADATAK: ceo objekat `{grupaId, vremeSekunde?, brojVarijanti?, nivo?}`.", required = false)
            ZadatakSlajda zadatak) {
        return McpGreske.izvrsi("izmeni_slajd", serija, poziv -> {
            poziv.id("slajd", slajdId);
            McpPatch p = McpPatch.iz(zahtev);
            return odgovor.json(prezentacije.izmeniSlajd(slajdId, new SlajdPatch(p.promena("naslov", String.class, true),
                    p.promena("sadrzaj", String.class, true), p.promena("slikaId", String.class, true),
                    p.promena("beleske", String.class, true), p.promena("postepeno", Boolean.class, false),
                    p.promena("prelaz", Prelaz.class, true), p.promena("pitanje", PitanjeCmd.class, false),
                    p.promena("zadatak", ZadatakSlajda.class, false), Promena.bez()), opis, serija));
        });
    }

    @McpTool(name = "zameni_platno", description = "Zamenjuje ceo dokument PLATNO slajda, povratno: pročitaj ga sa `slajd`, izmeni "
            + "(tekst, formula, položaj) i pošalji ceo nazad sa `otisak` iz tog čitanja. Drugačiji otisak znači da je slajd u "
            + "međuvremenu izmenjen: pročitaj ga ponovo. CRTEZ sloj sa istim id-jem i veličinom zadržava sačuvane poteze; ostali "
            + "dobijaju upozorenje. Važe ista pravila platna kao u editoru. Vraća `{slajd, izmenaId, upozorenja, link}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult zameniPlatno(
            CallToolRequest zahtev,
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id PLATNO slajda.") Long slajdId,
            @McpToolParam(description = "`otisak` iz odgovora alata `slajd`.") String otisak,
            @McpToolParam(description = "Šta se menja, 1 do 300 znakova.") String opis,
            @McpToolParam(description = "Ceo dokument platna, oblik kao `slajd.platno`.") Map<String, Object> platno) {
        return McpGreske.izvrsi("zameni_platno", serija, poziv -> {
            poziv.id("slajd", slajdId);
            return odgovor.json(prezentacije.zameniPlatno(slajdId, McpPatch.iz(zahtev).json("platno"), otisak, opis,
                    serija));
        });
    }

    @McpTool(name = "premesti_slajd", description = "Premešta slajd odmah iza slajda `posle`, a bez njega na početak prezentacije. "
            + "Redosled nije u istoriji: vraća se novim premeštanjem. Prezentacija koja se upravo izvodi se ne menja. Vraća sažetak slajda.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult premestiSlajd(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id slajda koji se premešta.") Long slajdId,
            @McpToolParam(description = "Id slajda iza kog ide; bez njega na početak.", required = false) Long posle) {
        return McpGreske.izvrsi("premesti_slajd", serija, poziv -> {
            poziv.id("slajd", slajdId);
            return odgovor.json(prezentacije.premestiSlajd(slajdId, posle, serija));
        });
    }

    @McpTool(name = "obrisi_slajd", description = "Briše slajd koji nikad nije korišćen na času (i Novicin), povratno: vraća `izmenaId`, "
            + "a `vrati_izmenu` ga obnavlja na istom mestu pod novim id-jem. Slajd sa rundama ili prikazan grupi je 409: tada reci Novici "
            + "šta se gubi i koristi `obrisi_koriscen_slajd`. Prezentacija koja se upravo izvodi se ne menja. Vraća `{izmenaId}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult obrisiSlajd(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id slajda.") Long slajdId) {
        return McpGreske.izvrsi("obrisi_slajd", serija, poziv -> {
            poziv.id("slajd", slajdId);
            return odgovor.json(Map.of("izmenaId", prezentacije.obrisiSlajd(slajdId, serija)));
        });
    }

    @McpTool(name = "obrisi_koriscen_slajd", description = "Briše slajd korišćen na času, samo kad Novica to izričito traži i uz njegovu "
            + "potvrdu: runde ostaju bez veze sa slajdom (rang ranijeg časa može da se promeni), a studenti ga više ne vide u pregledu. "
            + "`vrati_izmenu` obnavlja sadržaj, ali ne i te veze. Vraća `{izmenaId}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false),
            metaProvider = TraziPotvrdu.class)
    public CallToolResult obrisiKoriscenSlajd(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id slajda.") Long slajdId) {
        return McpGreske.izvrsi("obrisi_koriscen_slajd", serija, poziv -> {
            poziv.id("slajd", slajdId);
            return odgovor.json(Map.of("izmenaId", prezentacije.obrisiKoriscenSlajd(slajdId, serija)));
        });
    }

    @McpTool(name = "istorija_slajda", description = "Tvoje izmene i brisanja slajdova, najnovije prve, najviše 50: `slajdId` ili "
            + "`prezentacijaId` (tačno jedno). Unos ima `id` za `vrati_izmenu`, `opis`, `serija`, `kreirano`, `imaSnimak` i `obrisan`. "
            + "Novicine izmene u editoru se ovde ne beleže.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult istorijaSlajda(
            @McpToolParam(description = "Id slajda, i obrisanog.", required = false) Long slajdId,
            @McpToolParam(description = "Id prezentacije.", required = false) Long prezentacijaId) {
        return McpGreske.izvrsi("istorija_slajda", poziv -> odgovor.json(prezentacije.istorija(slajdId, prezentacijaId)));
    }

    @McpTool(name = "vrati_izmenu", description = "Vraća stanje slajda iz unosa istorije: postojeći slajd dobija staro stanje (i to "
            + "je novi unos, pa je i ovo povratno), obrisan slajd se obnavlja na svom mestu pod novim id-jem. Prolazi iste provere kao "
            + "svaki upis. Vraća `{radnja: \"vracen\"|\"obnovljen\", slajdId, izmenaId, link}`. " + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult vratiIzmenu(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id unosa iz `istorija_slajda`.") Long izmenaId) {
        return McpGreske.izvrsi("vrati_izmenu", serija, poziv -> {
            poziv.id("izmena", izmenaId);
            return odgovor.json(prezentacije.vratiIzmenu(izmenaId, serija));
        });
    }
}
