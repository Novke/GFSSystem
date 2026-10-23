package tri.novica.gfssystem.mcp.alati;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.banka.BankaFilter;
import tri.novica.gfssystem.dto.banka.GrupaCmd;
import tri.novica.gfssystem.dto.banka.GrupaDetails;
import tri.novica.gfssystem.dto.banka.GrupaPatch;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaInfo;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PonistavanjeInfo;
import tri.novica.gfssystem.dto.banka.PremestiCmd;
import tri.novica.gfssystem.dto.banka.StavkaPonistavanja;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.mcp.McpGreske;
import tri.novica.gfssystem.mcp.McpLinkovi;
import tri.novica.gfssystem.mcp.McpOdgovor;
import tri.novica.gfssystem.mcp.McpPatch;
import tri.novica.gfssystem.mcp.servis.McpBanka;
import tri.novica.gfssystem.mcp.servis.McpSaSlikom;
import tri.novica.gfssystem.mcp.servis.McpVarijanta;
import tri.novica.gfssystem.mcp.servis.ZahtevSlike;

import java.util.List;
import java.util.Map;

/**
 * MCP alati banke zadataka (spec MCP 4.3): čitanje ({@code stablo_banke}, {@code grupa}, {@code varijanta},
 * {@code pretraga_banke}), isključene teme ({@code iskljucene_teme}, {@code iskljuci_temu}, {@code ukljuci_temu}), grupe
 * ({@code napravi_grupu}, {@code izmeni_grupu}, {@code izmeni_polja_grupe}, {@code premesti_grupu}, {@code obrisi_grupu},
 * {@code kopiraj_grupu}) i {@code ponisti_seriju}. Tanki su: telo je {@link McpGreske#izvrsi} oko jedne metode fasade
 * {@link McpBanka}; upisni alat ide kroz varijantu sa serijom, koja je proverava pre posla. Prisustvo i {@code null}
 * ključeva (merge patch) i stroga šema polja se čitaju iz sirovih argumenata ({@link McpPatch}); tipizirani parametri tih
 * alata postoje zbog šeme alata.
 */
@Component
public class BankaAlati {

    private final McpBanka banka;
    private final McpOdgovor odgovor;
    private final McpLinkovi linkovi;

    public BankaAlati(McpBanka banka, McpOdgovor odgovor, McpLinkovi linkovi) {
        this.banka = banka;
        this.odgovor = odgovor;
        this.linkovi = linkovi;
    }

    /** Rezultat upisa grupe: grupa i link u UI-ju. */
    public record GrupaSaLinkom(GrupaDetails grupa, String link) {
    }

    /** {@link PonistavanjeInfo} sa linkom pregleda nacrta serije. */
    public record PonistavanjeSaLinkom(boolean potvrdjeno, List<StavkaPonistavanja> obrisano,
                                       List<StavkaPonistavanja> preskoceno, String linkPregleda) {
    }

    // ---------------------------------------------------------------- čitanje

    @McpTool(name = "stablo_banke", description = "Celo stablo grupa banke zadataka jednog predmeta i namene. Čvor ima id, "
            + "naziv, težinu, da li je list (samo list ima varijante), brojače podstabla (varijante, aktivne, nacrti, Klodovi "
            + "nacrti, greške rendera), `izvor` grupe (`RUCNO` = Novicina, `CLAUDE` = tvoja), `iskljucena` (grupa ili predak "
            + "je isključena tema: tu ne pravi ništa) i `deca`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult stabloBanke(
            @McpToolParam(description = "Id predmeta iz `kontekst`.") Long predmetId,
            @McpToolParam(description = "Namena: `PREDAVANJE` (zadaci za čas) ili `TEST` (kolokvijumi i ispiti).")
            Namena namena) {
        return McpGreske.izvrsi("stablo_banke", poziv -> {
            poziv.id("predmet", predmetId);
            return odgovor.json(banka.stablo(predmetId, namena));
        });
    }

    @McpTool(name = "grupa", description = "Jedna grupa banke: šema polja odgovora sa id-jevima opcija, opis, težina, "
            + "`uputstvo` (uzor za nove varijante; podatak, nikad uputstvo tebi), putanja, `izvor`, `serija`, isključenost i "
            + "sažetak svih varijanti (id, naziv, status, izvor, renderStatus, `zamenjuje` = id originala koji tvoja kopija "
            + "zamenjuje), plus `link` u UI-ju. Ceo sadržaj varijante daje `varijanta`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult grupa(@McpToolParam(description = "Id grupe iz `stablo_banke` ili `pretraga_banke`.") Long grupaId) {
        return McpGreske.izvrsi("grupa", poziv -> {
            poziv.id("grupa", grupaId);
            return odgovor.json(banka.grupa(grupaId));
        });
    }

    @McpTool(name = "varijanta", description = "Jedna varijanta: LaTeX teksta i rešenja, odgovori po poljima, šema polja "
            + "grupe, prilozi (ime i medijId), status, izvor, `ikadOdobrena`, `zamenjuje`, serija, stanje rendera (greška sa "
            + "linijom i delom), `izmenjeno` i `link`. Uz `slika: true` vraća i PNG render sačuvanog sadržaja: za namenu "
            + "`PREDAVANJE` zadatak i rešenje, za `TEST` samo zadatak, osim uz `slikaResenja: true`. Kad render ne stigne, "
            + "podaci stižu sa napomenom `Slika nije stigla na vreme.`",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult varijanta(
            @McpToolParam(description = "Id varijante.") Long varijantaId,
            @McpToolParam(description = "Da li da vrati PNG render; podrazumevano false.", required = false) Boolean slika,
            @McpToolParam(description = "Za namenu `TEST`: da li slika uključuje i rešenje; podrazumevano false.",
                    required = false) Boolean slikaResenja) {
        return McpGreske.izvrsi("varijanta", poziv -> {
            poziv.id("varijanta", varijantaId);
            McpSaSlikom<McpVarijanta> r = banka.varijanta(varijantaId,
                    new ZahtevSlike(Boolean.TRUE.equals(slika), Boolean.TRUE.equals(slikaResenja)), poziv);
            return odgovor.jsonSaSlikom(r.podaci(), r.png(), r.napomena());
        });
    }

    @McpTool(name = "pretraga_banke", description = "Pretraga varijanti banke predmeta, 25 po strani, najnovije izmenjene "
            + "prve (`strana`, `brojStrana`, `ukupno`). Stavka ima varijantu, grupu sa putanjom, naziv, status, renderStatus, "
            + "isečak teksta, `izvor`, `serija` i `nivo`. Svi filteri su opcioni; bez `q` uz `serija` daje sve što je ta serija "
            + "napravila. Pre uvoza proveri da zadatak već ne postoji.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult pretragaBanke(
            @McpToolParam(description = "Id predmeta iz `kontekst`.") Long predmetId,
            @McpToolParam(description = "Tekst u nazivu ili tekstu varijante ili u nazivu grupe, bez obzira na velika "
                    + "slova; kad je zadat, bar 2 znaka.", required = false) String q,
            @McpToolParam(description = "Namena: `PREDAVANJE` ili `TEST`.", required = false) Namena namena,
            @McpToolParam(description = "Status: `NACRT`, `AKTIVNA` ili `ARHIVIRANA`.", required = false)
            StatusVarijante status,
            @McpToolParam(description = "Izvor varijante: `RUCNO` (Novica) ili `CLAUDE` (ti).", required = false)
            IzvorVarijante izvor,
            @McpToolParam(description = "Serija upisa (tačno poklapanje).", required = false) String serija,
            @McpToolParam(description = "Nivo varijante: 1 (lakša), 2 (srednja) ili 3 (teža) unutar svog tipa.",
                    required = false) Integer nivo,
            @McpToolParam(description = "Strana od 1; podrazumevano 1.", required = false) Integer strana) {
        return McpGreske.izvrsi("pretraga_banke", poziv -> {
            poziv.id("predmet", predmetId);
            return odgovor.json(banka.pretraga(new BankaFilter(predmetId, namena, q, status, izvor, serija, nivo),
                    strana == null ? 1 : strana));
        });
    }

    // ---------------------------------------------------------------- isključene teme

    @McpTool(name = "iskljucene_teme", description = "Isključene teme predmeta, najnovije prve: opis, grupa sa putanjom "
            + "(isključuje tu grupu i celo podstablo; bez grupe je samo opis teme koju ne praviš), izvor, serija i vreme. Za te "
            + "teme ne pravi ni grupe ni varijante.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult iskljuceneTeme(@McpToolParam(description = "Id predmeta iz `kontekst`.") Long predmetId) {
        return McpGreske.izvrsi("iskljucene_teme", poziv -> {
            poziv.id("predmet", predmetId);
            return odgovor.json(banka.iskljuceneTeme(predmetId));
        });
    }

    @McpTool(name = "iskljuci_temu", description = "Isključuje temu predmeta, samo kad Novica to izričito traži: sa "
            + "`grupaId` isključuje tu grupu i celo podstablo, bez nje upisuje opis teme koja u banci još ne postoji. Ponovljen "
            + "poziv za istu grupu ili isti opis bez grupe je greška `Ova grupa je već isključena.` ili `Ova tema je već "
            + "isključena.`, nikad drugi red.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult iskljuciTemu(
            @McpToolParam(description = "Serija ovog zahteva, npr. `uvoz-kol1-2024` (3 do 60 malih slova, cifara ili "
                    + "crtica).") String serija,
            @McpToolParam(description = "Id predmeta iz `kontekst`.") Long predmetId,
            @McpToolParam(description = "Opis teme, 1 do 300 znakova, npr. `Razlomljeni brojevi 10 → 2`.") String opis,
            @McpToolParam(description = "Id grupe istog predmeta koja se isključuje sa podstablom.", required = false)
            Long grupaId) {
        return McpGreske.izvrsi("iskljuci_temu", serija, poziv -> {
            poziv.id("predmet", predmetId);
            if (grupaId != null) {
                poziv.id("grupa", grupaId);
            }
            IskljucenaTemaInfo tema = banka.iskljuciTemu(predmetId, opis, grupaId, serija);
            poziv.id("tema", tema.id());
            return odgovor.json(tema);
        });
    }

    @McpTool(name = "ukljuci_temu", description = "Uključuje isključenu temu (briše isključenje, i Novicino), samo kad "
            + "Novica to traži; vraća `{ukljuceno: true}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false),
            metaProvider = TraziPotvrdu.class)
    public CallToolResult ukljuciTemu(@McpToolParam(description = "Id teme iz `iskljucene_teme`.") Long temaId) {
        return McpGreske.izvrsi("ukljuci_temu", poziv -> {
            poziv.id("tema", temaId);
            banka.ukljuciTemu(temaId);
            return odgovor.json(Map.of("ukljuceno", true));
        });
    }

    // ---------------------------------------------------------------- grupe

    @McpTool(name = "napravi_grupu", description = "Nova grupa banke na kraj braće, sa izvorom `CLAUDE` i tvojom serijom: "
            + "koren (`predmetId` i `namena`, bez `roditeljId`) ili podgrupa (`roditeljId`; predmet i namena se nasleđuju od "
            + "roditelja), i pod Novicinom grupom. Varijante ima samo list, pa grupa sa varijantama ne dobija podgrupe; "
            + "dubina je najviše 6, naziv je jedinstven među braćom. List pre prve varijante treba `tezina` 1-5; `polja` je "
            + "šema odgovora (vodič `banka`), opcija bez `id` dobija nov id. Vraća `{grupa, link}`. " + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult napraviGrupu(
            CallToolRequest zahtev,
            @McpToolParam(description = "Serija ovog zahteva, npr. `uvoz-kol1-2024` (3 do 60 malih slova, cifara ili "
                    + "crtica).") String serija,
            @McpToolParam(description = "Id predmeta iz `kontekst`.") Long predmetId,
            @McpToolParam(description = "Namena korena: `PREDAVANJE` ili `TEST`; podgrupa je nasleđuje.", required = false)
            Namena namena,
            @McpToolParam(description = "Id roditeljske grupe; bez njega je grupa koren.", required = false) Long roditeljId,
            @McpToolParam(description = "Naziv, jedinstven među braćom.") String naziv,
            @McpToolParam(description = "Opis grupe, najviše 5 000 znakova.", required = false) String opis,
            @McpToolParam(description = "Težina 1-5, ceo broj; obavezna za list pre prve varijante.", required = false)
            Integer tezina,
            @McpToolParam(description = "Uputstvo za varijante grupe (stil, opseg brojeva, šta izbegavati).",
                    required = false) String uputstvo,
            @McpToolParam(description = "Šema polja odgovora (`kljuc`, `oznaka`, `tip`, ...; vodič `banka`).",
                    required = false) List<Polje> polja) {
        return McpGreske.izvrsi("napravi_grupu", serija, poziv -> {
            poziv.id("predmet", predmetId);
            if (roditeljId != null) {
                poziv.id("roditelj", roditeljId);
            }
            McpPatch argumenti = McpPatch.iz(zahtev);
            // težina samo kao ceo broj (kao REST): vezivanje Spring AI-ja bi 2.5 skratilo na 2
            GrupaDetails g = banka.napraviGrupu(new GrupaCmd(predmetId, roditeljId, namena, naziv, opis,
                    argumenti.strogo("tezina", Integer.class), uputstvo, argumenti.polja("polja")), serija);
            poziv.id("grupa", g.id());
            return grupa(g);
        });
    }

    @McpTool(name = "izmeni_grupu", description = "Delimična izmena tvoje grupe (izvor `CLAUDE`): izostavljen ključ ostaje "
            + "kakav jeste, `null` briše `opis`, `tezina` ili `uputstvo` (`naziv` se ne briše). Šemu polja menja "
            + "`izmeni_polja_grupe`. Novicina grupa (`RUCNO`) je greška: reci Novici da je izmeni u UI-ju. Vraća "
            + "`{grupa, link}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult izmeniGrupu(
            CallToolRequest zahtev,
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id tvoje grupe.") Long grupaId,
            @McpToolParam(description = "Nov naziv, jedinstven među braćom.", required = false) String naziv,
            @McpToolParam(description = "Nov opis; `null` briše.", required = false) String opis,
            @McpToolParam(description = "Nova težina 1-5; `null` briše (samo grupi bez varijanti).", required = false)
            Integer tezina,
            @McpToolParam(description = "Novo uputstvo; `null` briše.", required = false) String uputstvo) {
        return McpGreske.izvrsi("izmeni_grupu", serija, poziv -> {
            poziv.id("grupa", grupaId);
            McpPatch patch = McpPatch.iz(zahtev);
            return grupa(banka.izmeniGrupu(grupaId, new GrupaPatch(patch.promena("naziv", String.class, false),
                    patch.promena("opis", String.class, true), patch.promena("tezina", Integer.class, true),
                    patch.promena("uputstvo", String.class, true)), serija));
        });
    }

    @McpTool(name = "izmeni_polja_grupe", description = "Nova cela šema polja tvoje grupe (izvor `CLAUDE`). Za opciju koju "
            + "zadržavaš vrati njen `id` (preimenovanje = isti `id`); opcija bez `id` je nova, a izostavljena se briše. Pre "
            + "upisa server isproba izmenu: kad bi ijedna varijanta izgubila odgovor ili ijedna `AKTIVNA` pala u nacrt, "
            + "greška navodi pogođene varijante i ništa se ne upisuje. Vraća `{grupa, link}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false),
            metaProvider = TraziPotvrdu.class)
    public CallToolResult izmeniPoljaGrupe(
            CallToolRequest zahtev,
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id tvoje grupe.") Long grupaId,
            @McpToolParam(description = "Cela nova šema polja (vodič `banka`); `[]` briše šemu.") List<Polje> polja) {
        return McpGreske.izvrsi("izmeni_polja_grupe", serija, poziv -> {
            poziv.id("grupa", grupaId);
            return grupa(banka.izmeniPoljaGrupe(grupaId, McpPatch.iz(zahtev).polja("polja"), serija));
        });
    }

    @McpTool(name = "premesti_grupu", description = "Premešta tvoju grupu (izvor `CLAUDE`) sa celim podstablom pod drugog "
            + "roditelja istog predmeta i namene (i pod Novicinu grupu) ili u korene, na mesto `rb` među novom braćom. "
            + "Vraća `{grupa, link}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult premestiGrupu(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id tvoje grupe.") Long grupaId,
            @McpToolParam(description = "Id novog roditelja; bez njega grupa postaje koren.", required = false)
            Long roditeljId,
            @McpToolParam(description = "Mesto među novom braćom od 1; bez njega na kraj.", required = false) Integer rb) {
        return McpGreske.izvrsi("premesti_grupu", serija, poziv -> {
            poziv.id("grupa", grupaId);
            if (roditeljId != null) {
                poziv.id("roditelj", roditeljId);
            }
            return grupa(banka.premestiGrupu(grupaId, new PremestiCmd(roditeljId, rb), serija));
        });
    }

    @McpTool(name = "obrisi_grupu", description = "Briše tvoju praznu grupu (izvor `CLAUDE`, bez varijanti i podgrupa); "
            + "vraća `{obrisano: true}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false),
            metaProvider = TraziPotvrdu.class)
    public CallToolResult obrisiGrupu(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id tvoje prazne grupe.") Long grupaId) {
        return McpGreske.izvrsi("obrisi_grupu", serija, poziv -> {
            poziv.id("grupa", grupaId);
            banka.obrisiGrupu(grupaId, serija);
            return odgovor.json(Map.of("obrisano", true));
        });
    }

    @McpTool(name = "kopiraj_grupu", description = "Kopira grupu (i Novicinu) sa celim podstablom u namenu `ciljNamena` "
            + "istog predmeta, kao koren ili pod `ciljRoditeljId` u ciljnom stablu: isti naziv, opis, težina, uputstvo i "
            + "šema polja sa istim id-jevima opcija; varijante osim arhiviranih postaju tvoji nacrti iz serije. Sve ili ništa. "
            + "Vraća kopiju korena `{grupa, link}`. " + McpAlati.NE_PONAVLJAJ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = false))
    public CallToolResult kopirajGrupu(
            @McpToolParam(description = "Serija ovog zahteva.") String serija,
            @McpToolParam(description = "Id grupe koja se kopira.") Long grupaId,
            @McpToolParam(description = "Namena kopije: `PREDAVANJE` ili `TEST`.") Namena ciljNamena,
            @McpToolParam(description = "Id roditelja u ciljnom stablu (isti predmet i `ciljNamena`); bez njega je kopija "
                    + "koren.", required = false) Long ciljRoditeljId) {
        return McpGreske.izvrsi("kopiraj_grupu", serija, poziv -> {
            poziv.id("grupa", grupaId);
            GrupaDetails kopija = banka.kopirajGrupu(grupaId, ciljNamena, ciljRoditeljId, serija);
            poziv.id("kopija", kopija.id());
            return grupa(kopija);
        });
    }

    // ---------------------------------------------------------------- poništavanje serije

    @McpTool(name = "ponisti_seriju", description = "Poništava ono što je serija napravila a još sme da se obriše: tvoje "
            + "neodobrene nacrte, tvoje grupe koje time ostanu prazne, slajdove serije i prezentacije serije koje nikad nisu "
            + "izvođene; ostalo (odobreno, Novicino, izvođeno) navodi kao preskočeno sa razlogom. Nijedan slajd van serije se "
            + "ne briše: prezentacija serije u koju je Novica dodao ili duplirao slajd (ili u kojoj je slajd druge serije) ostaje "
            + "(preskočena: `Prezentacija ima "
            + "Novicine slajdove.`), a iz nje se brišu samo slajdovi serije. Prvo pozovi sa `potvrdi: false` (samo spisak, "
            + "ništa se ne briše), pokaži ga Novici i tek posle njegove potvrde pozovi sa `potvrdi: true`. Vraća "
            + "`{potvrdjeno, obrisano, preskoceno, linkPregleda}`.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false),
            metaProvider = TraziPotvrdu.class)
    public CallToolResult ponistiSeriju(
            @McpToolParam(description = "Serija koja se poništava.") String serija,
            @McpToolParam(description = "`false` = samo spisak onoga što bi se obrisalo, `true` = briše.") Boolean potvrdi) {
        return McpGreske.izvrsi("ponisti_seriju", serija, poziv -> {
            // bez potvrde (i bez argumenta) samo spisak: brisanje traži izričito true
            PonistavanjeInfo p = banka.ponistiSeriju(serija, Boolean.TRUE.equals(potvrdi));
            return odgovor.json(new PonistavanjeSaLinkom(p.potvrdjeno(), p.obrisano(), p.preskoceno(),
                    linkovi.pregled(serija)));
        });
    }

    private CallToolResult grupa(GrupaDetails g) {
        return odgovor.json(new GrupaSaLinkom(g, linkovi.grupa(g.id())));
    }
}
