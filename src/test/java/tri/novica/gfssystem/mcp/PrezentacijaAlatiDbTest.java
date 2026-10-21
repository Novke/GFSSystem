package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.PlatnoTestPodaci;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Alati prezentacija kroz pravi {@code /mcp} (spec MCP 4.4): {@code prezentacije}, {@code prezentacija} (sažet pregled),
 * {@code slajd} (pun, PLATNO bez poteza i sa brojem tačaka po crtežu), {@code napravi_prezentaciju} (serija, link, jedno
 * pravljenje) i {@code dodaj_slajd} (samo INFO, PITANJE i ZADATAK, serija na slajdu, strogo čitanje {@code slajd}, Markdown slike
 * samo iz GFS-a, prezentacija koja se upravo izvodi se ne menja). Ništa od odbijenog nije upisano.
 */
class PrezentacijaAlatiDbTest extends McpDbTestOsnova {

    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final String SAMO_INFO_I_PITANJE = "Klod u ovoj verziji dodaje samo INFO, PITANJE i ZADATAK slajdove.";
    static final String IZVODI_SE = "Prezentacija se upravo izvodi; izmene posle časa.";
    static final AtomicInteger KOD = new AtomicInteger((int) (Math.random() * 400_000) + 100_000);

    @Autowired PrezentacijaService prezentacijaService;
    @MockitoSpyBean McpBudzet budzet;

    Predmet predmet;
    String serija;
    /** Novicina prezentacija (bez serije), prazna. */
    Long prezentacija;

    @BeforeEach
    void setUp() {
        predmet = noviPredmet("Prezentacije MCP test");
        serija = "prez-" + UUID.randomUUID().toString().substring(0, 8);
        prezentacija = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Vežbe 1", null, null)).id();
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    JsonNode uspeh(String alat, Map<String, Object> argumenti) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertFalse(McpTestKlijent.greska(r), r.toString());
        return McpTestKlijent.json(r);
    }

    void ocekujGresku(String alat, Map<String, Object> argumenti, String poruka) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertTrue(McpTestKlijent.greska(r), r.toString());
        assertEquals(poruka, McpTestKlijent.tekst(r));
    }

    long brojSlajdova(Long prezentacijaId) {
        Long n = jdbc.queryForObject("select count(*) from slajdovi where prezentacija_id = ?", Long.class, prezentacijaId);
        return n == null ? 0 : n;
    }

    Map<String, Object> dodaj(Long prezentacijaId, Object slajd) {
        return mapa("serija", serija, "prezentacijaId", prezentacijaId, "slajd", slajd);
    }

    static Map<String, Object> info(String naslov) {
        return mapa("tip", "INFO", "naslov", naslov, "sadrzaj", "- osnova 2\n- osnova 8", "postepeno", true);
    }

    static Map<String, Object> jedanTacan() {
        return mapa("tip", "PITANJE", "pitanje", mapa("tip", "JEDAN_TACAN", "tekst", "Koliko je $1011_{(2)}$?",
                "vremeSekunde", 30, "opcije", List.of(mapa("tekst", "9", "tacna", false), mapa("tekst", "11", "tacna", true))));
    }

    String slika() {
        String id = medijService.sacuvaj(new MockMultipartFile("fajl", "a.png", "image/png", PNG)).id();
        mediji.add(id);
        return id;
    }

    /** Izvođenje kroz JDBC, kao u {@code PrezentacijaServiceMcpDbTest}: aktivno (sa kodom). */
    void aktivnoIzvodjenje(Long prezentacijaId) {
        String kod = String.valueOf(KOD.incrementAndGet());
        jdbc.update("""
                insert into izvodjenja (prezentacija_id, kod, aktivan_kod, cuvanje, status, pocetak, prikaz, korak,
                  rezultati_prikazani, tacan_prikazan, rang_lista_prikazana, ekran, qr_prikazan, telefon_prikaz,
                  detalji_dozvoljeni, takmicenje, verzija)
                values (?, ?, ?, 1, 'AKTIVNO', now(6), 'PRIJAVA', 0, 0, 0, 0, 'NORMALAN', 0, 'DUGMAD', 1, 0, 1)""",
                prezentacijaId, kod, kod);
    }

    // ---------------------------------------------------------------- napravi_prezentaciju

    @Test
    void napraviPrezentacijuPiseSerijuIVracaLink() {
        JsonNode r = uspeh("napravi_prezentaciju", mapa("serija", serija, "predmetId", predmet.getId(),
                "naziv", "Vežbe 2: konverzije", "opis", "Brojni sistemi.", "tema", "PAPIR"));
        assertEquals(Set.of("prezentacija", "link"), new TreeSet<>(r.propertyNames()), r.toString());
        JsonNode p = r.path("prezentacija");
        long id = p.path("id").asLong();
        assertEquals("http://localhost:4200/prezentacije/" + id, r.path("link").asString());
        assertEquals("Vežbe 2: konverzije", p.path("naziv").asString());
        assertEquals("Brojni sistemi.", p.path("opis").asString());
        assertEquals("PAPIR", p.path("tema").asString());
        assertEquals(0, p.path("brojSlajdova").asInt());
        assertEquals(predmet.getId(), p.path("predmet").path("id").asLong());
        assertFalse(p.has("slajdovi"));
        assertEquals(serija, jdbc.queryForObject("select serija from prezentacije where id = ?", String.class, id));
        verify(budzet, times(1)).trosiPravljenje("test", 1);
    }

    @Test
    void napraviPrezentacijuBezNazivaIPrekoBudzetaNeUpisujeNista() {
        Long pre = jdbc.queryForObject("select count(*) from prezentacije where predmet_id = ?", Long.class, predmet.getId());
        ocekujGresku("napravi_prezentaciju", mapa("serija", serija, "predmetId", predmet.getId(), "naziv", " "),
                "Naziv je obavezan (najviše 200 znakova).");
        doThrow(new SystemException(McpBudzet.PORUKA, 429)).when(budzet).trosiPravljenje(anyString(), anyInt());
        ocekujGresku("napravi_prezentaciju", mapa("serija", serija, "predmetId", predmet.getId(), "naziv", "Vežbe"),
                McpBudzet.PORUKA);
        assertEquals(pre, jdbc.queryForObject("select count(*) from prezentacije where predmet_id = ?", Long.class,
                predmet.getId()));
    }

    // ---------------------------------------------------------------- dodaj_slajd

    @Test
    void dodajSlajdInfoIPitanjeRedomSaSerijom() {
        JsonNode prvi = uspeh("dodaj_slajd", dodaj(prezentacija, info("Pozicioni sistemi")));
        assertEquals(Set.of("slajd", "link"), new TreeSet<>(prvi.propertyNames()), prvi.toString());
        assertEquals("http://localhost:4200/prezentacije/" + prezentacija, prvi.path("link").asString());
        JsonNode s1 = prvi.path("slajd");
        assertEquals(1, s1.path("rb").asInt());
        assertEquals("INFO", s1.path("tip").asString());
        assertEquals("Pozicioni sistemi", s1.path("naslov").asString());
        assertEquals(2, s1.path("brojKoraka").asInt());

        JsonNode s2 = uspeh("dodaj_slajd", dodaj(prezentacija, jedanTacan())).path("slajd");
        assertEquals(2, s2.path("rb").asInt());
        assertEquals("PITANJE", s2.path("tip").asString());
        assertEquals("JEDAN_TACAN", s2.path("pitanje").path("tip").asString());
        assertEquals("11", s2.path("pitanje").path("opcije").path(1).path("tekst").asString());
        assertTrue(s2.path("pitanje").path("opcije").path(1).path("tacna").asBoolean());
        assertFalse(s2.path("pitanje").path("opcije").path(0).path("tacna").asBoolean());

        // posle prvog: novi je drugi, pitanje treće
        JsonNode s3 = uspeh("dodaj_slajd", mapa("serija", serija, "prezentacijaId", prezentacija,
                "posle", s1.path("id").asLong(), "slajd", info("Između"))).path("slajd");
        assertEquals(2, s3.path("rb").asInt());
        assertEquals(List.of(s1.path("id").asLong(), s3.path("id").asLong(), s2.path("id").asLong()),
                jdbc.queryForList("select id from slajdovi where prezentacija_id = ? order by rb", Long.class, prezentacija));
        assertEquals(List.of(serija, serija, serija), jdbc.queryForList(
                "select serija from slajdovi where prezentacija_id = ? order by rb", String.class, prezentacija));
        verify(budzet, times(3)).trosiPravljenje("test", 1);
    }

    @Test
    void platnoSlajdJe400SaPorukomTipaINistaNijeUpisano() {
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, mapa("tip", "PLATNO")), SAMO_INFO_I_PITANJE);
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, mapa("tip", "PLATNO", "platno",
                PlatnoTestPodaci.cvor(PlatnoTestPodaci.sKlikovima(1)))), SAMO_INFO_I_PITANJE);
        assertEquals(0, brojSlajdova(prezentacija));
        verify(budzet, never()).trosiPravljenje(anyString(), anyInt());
    }

    @Test
    void slikaVanGfsaUMarkdownuJe400() {
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, mapa("tip", "INFO", "naslov", "a",
                "sadrzaj", "![x](https://x.invalid/a.png)")), "Slika u tekstu mora biti otpremljena u GFS.");
        assertEquals(0, brojSlajdova(prezentacija));

        String slika = slika();
        JsonNode s = uspeh("dodaj_slajd", dodaj(prezentacija, mapa("tip", "INFO", "naslov", "a",
                "sadrzaj", "![tabla](api/public/mediji/" + slika + ")"))).path("slajd");
        assertEquals(1, s.path("rb").asInt());
    }

    @Test
    void slajdSeCitaStrogo() {
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, mapa("tip", "INFO", "naslov", "a", "x", 1)),
                "Nepoznato polje 'slajd.x'.");
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, mapa("tip", "INFO", "naslov", "a", "postepeno", null)),
                McpGreske.NEISPRAVAN_FORMAT);
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, mapa("tip", "PITANJE", "pitanje",
                mapa("tip", "JEDAN_TACAN", "x", 1))), "Nepoznato polje 'slajd.pitanje.x'.");
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, mapa("tip", "PITANJE", "pitanje", mapa("tip", "JEDAN_TACAN",
                "tekst", "a", "opcije", List.of(mapa("tekst", "9", "tacna", null))))), McpGreske.NEISPRAVAN_FORMAT);
        ocekujGresku("dodaj_slajd", mapa("serija", serija, "prezentacijaId", prezentacija), McpGreske.NEISPRAVAN_FORMAT);
        assertEquals(0, brojSlajdova(prezentacija));
        verify(budzet, never()).trosiPravljenje(anyString(), anyInt());
    }

    @Test
    void dokSeIzvodiPrezentacijaSeNeMenja() {
        aktivnoIzvodjenje(prezentacija);
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, info("a")), IZVODI_SE);
        assertEquals(0, brojSlajdova(prezentacija));
        JsonNode pregled = uspeh("prezentacija", mapa("prezentacijaId", prezentacija));
        assertTrue(pregled.path("aktivnoIzvodjenjeId").asLong() > 0, pregled.toString());
    }

    @Test
    void nepostojecaPrezentacijaIPosleIzDrugeSu404I400() {
        ocekujGresku("dodaj_slajd", dodaj(Long.MAX_VALUE, info("a")), "Prezentacija nije pronađena.");
        Long druga = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Druga", null, null)).id();
        Long tudji = prezentacijaService.dodajSlajd(druga, new SlajdCmd(TipSlajda.INFO, "t", null, null, null, false,
                null, null, null), null).id();
        ocekujGresku("dodaj_slajd", mapa("serija", serija, "prezentacijaId", prezentacija, "posle", tudji,
                "slajd", info("a")), "Slajd posle kog se dodaje nije u ovoj prezentaciji.");
        assertEquals(0, brojSlajdova(prezentacija));
    }

    // ---------------------------------------------------------------- čitanje

    @Test
    void slajdPlatnoBezPotezaSaBrojemTacakaPoCrtezu() {
        String slika = slika();
        String crtez = slika();
        Long id = prezentacijaService.dodajSlajd(prezentacija, new SlajdCmd(TipSlajda.PLATNO, null, null, null,
                "Beleške.", false, null, PlatnoTestPodaci.cvor(PlatnoTestPodaci.ispravno(slika, crtez)), null), null).id();
        JsonNode r = mcp.pozovi("slajd", mapa("slajdId", id));
        assertFalse(McpTestKlijent.greska(r), r.toString());
        assertFalse(McpTestKlijent.tekst(r).contains("\"tacke\""), McpTestKlijent.tekst(r));
        JsonNode j = McpTestKlijent.json(r);
        assertEquals(Set.of("slajd", "brojTacaka", "link"), new TreeSet<>(j.propertyNames()), j.toString());
        assertEquals("http://localhost:4200/prezentacije/" + prezentacija, j.path("link").asString());
        assertEquals(3, j.path("brojTacaka").path("c1").asInt());
        assertEquals(1, j.path("brojTacaka").size());
        JsonNode s = j.path("slajd");
        assertEquals(id, s.path("id").asLong());
        assertEquals("PLATNO", s.path("tip").asString());
        assertEquals("Beleške.", s.path("beleske").asString());
        JsonNode c1 = null;
        for (JsonNode sloj : s.path("platno").path("slojevi")) {
            if ("c1".equals(sloj.path("id").asString())) {
                c1 = sloj;
            }
        }
        assertNotNull(c1, s.toString());
        assertEquals(crtez, c1.path("medijId").asString());
        assertTrue(c1.path("potezi").isNull(), c1.toString());
        assertEquals(7, s.path("platno").path("slojevi").size());

        // REST i dalje vraća poteze (front ih koristi u editoru)
        assertEquals(1, prezentacijaService.slajd(id).platno().slojevi().stream()
                .filter(sl -> sl instanceof tri.novica.gfssystem.dto.uzivo.platno.CrtezSloj c && c.potezi() != null)
                .count());
    }

    @Test
    void slajdInfoINepostojeci() {
        Long id = prezentacijaService.dodajSlajd(prezentacija, new SlajdCmd(TipSlajda.INFO, "Naslov", "Tekst", null,
                null, false, null, null, null), null).id();
        JsonNode j = uspeh("slajd", mapa("slajdId", id));
        assertEquals("Naslov", j.path("slajd").path("naslov").asString());
        assertTrue(j.path("brojTacaka").isNull() || j.path("brojTacaka").isEmpty(), j.toString());
        ocekujGresku("slajd", mapa("slajdId", Long.MAX_VALUE), "Slajd nije pronađen.");
    }

    @Test
    void prezentacijaVracaSazetakIPrezentacijeListu() {
        String slika = slika();
        String crtez = slika();
        uspeh("dodaj_slajd", dodaj(prezentacija, info("Pozicioni sistemi")));
        prezentacijaService.dodajSlajd(prezentacija, new SlajdCmd(TipSlajda.PLATNO, null, null, null, null, false, null,
                PlatnoTestPodaci.cvor(PlatnoTestPodaci.ispravno(slika, crtez)), null), null);
        uspeh("dodaj_slajd", dodaj(prezentacija, jedanTacan()));

        JsonNode r = mcp.pozovi("prezentacija", mapa("prezentacijaId", prezentacija));
        assertFalse(McpTestKlijent.greska(r), r.toString());
        assertFalse(McpTestKlijent.tekst(r).contains("potezi"), McpTestKlijent.tekst(r));
        JsonNode p = McpTestKlijent.json(r);
        assertEquals(prezentacija, p.path("id").asLong());
        assertEquals("Vežbe 1", p.path("naziv").asString());
        assertEquals("NACRT", p.path("tema").asString());
        assertEquals(3, p.path("brojSlajdova").asInt());
        assertEquals(1, p.path("strana").asInt());
        assertEquals(1, p.path("brojStrana").asInt());
        assertEquals("http://localhost:4200/prezentacije/" + prezentacija, p.path("link").asString());
        assertFalse(p.has("aktivnoIzvodjenjeId"), p.toString());
        JsonNode slajdovi = p.path("slajdovi");
        assertEquals(List.of("INFO", "PLATNO", "PITANJE"), List.of(slajdovi.path(0).path("tip").asString(),
                slajdovi.path(1).path("tip").asString(), slajdovi.path(2).path("tip").asString()));
        JsonNode platno = slajdovi.path(1);
        assertEquals(2, platno.path("brojKoraka").asInt());
        assertEquals(7, platno.path("slojevi").size());
        assertEquals("Prvi pasus\nDrugi pasus", platno.path("slojevi").path(0).path("tekst").asString());
        assertEquals(3, platno.path("slojevi").path(6).path("brojTacaka").asInt());
        assertEquals("Koliko je $1011_{(2)}$?", slajdovi.path(2).path("pitanje").path("tekst").asString());

        ocekujGresku("prezentacija", mapa("prezentacijaId", prezentacija, "strana", 2), "Neispravan parametar: strana.");
        ocekujGresku("prezentacija", mapa("prezentacijaId", Long.MAX_VALUE), "Prezentacija nije pronađena.");

        JsonNode lista = mcp.pozovi("prezentacije", mapa("predmetId", predmet.getId()));
        assertFalse(McpTestKlijent.greska(lista), lista.toString());
        JsonNode stavke = McpTestKlijent.json(lista);
        assertEquals(1, stavke.size());
        assertEquals(prezentacija, stavke.path(0).path("id").asLong());
        assertEquals(3, stavke.path(0).path("brojSlajdova").asInt());
        assertEquals(1, stavke.path(0).path("brojPitanja").asInt());
        ocekujGresku("prezentacije", Map.of(), "Predmet je obavezan.");
    }

    // ---------------------------------------------------------------- ZADATAK

    long grupa(Long roditelj, String naziv, String namena) {
        jdbc.update("""
                insert into grupe_zadataka (predmet_id, roditelj_id, namena, naziv, rb, tezina, polja, izvor, kreirano, izmenjeno)
                values (?, ?, ?, ?, 1, ?, '[]', 'RUCNO', now(6), now(6))""",
                predmet.getId(), roditelj, namena, naziv, roditelj == null ? null : 3);
        return jdbc.queryForObject("select max(id) from grupe_zadataka where predmet_id = ?", Long.class, predmet.getId());
    }

    void aktivna(long grupa, String naziv) {
        String svg = medijService.sacuvajRender("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(), "r.svg").id();
        jdbc.update("""
                insert into varijante (grupa_id, rb, naziv, tekst, odgovori, status, izvor, ikad_odobrena, render_status,
                  render_svg_id, kreirano, izmenjeno)
                values (?, 1, ?, 'Zadatak', '{}', 'AKTIVNA', 'RUCNO', 1, 'OK', ?, now(6), now(6))""", grupa, naziv, svg);
    }

    static Map<String, Object> zadatak(Object... kv) {
        return mapa("tip", "ZADATAK", "zadatak", mapa(kv));
    }

    @Test
    void dodajSlajdZadatakSaPutanjomIBrojemAktivnih() {
        long koren = grupa(null, "Konverzije", "PREDAVANJE");
        long list = grupa(koren, "2 → 10", "PREDAVANJE");
        for (int i = 1; i <= 3; i++) aktivna(list, "V" + i);

        JsonNode s = uspeh("dodaj_slajd", dodaj(prezentacija, zadatak("grupaId", list, "brojVarijanti", 4))).path("slajd");
        assertEquals("ZADATAK", s.path("tip").asString());
        assertEquals(0, s.path("brojKoraka").asInt());
        JsonNode z = s.path("zadatak");
        assertEquals(List.of("Konverzije", "2 → 10"), List.of(z.path("putanja").path(0).path("naziv").asString(),
                z.path("putanja").path(1).path("naziv").asString()));
        assertEquals(3, z.path("brojAktivnih").asInt());
        assertEquals(4, z.path("brojVarijanti").asInt());
        assertFalse(z.has("grupaNaziv"), z.toString());
        assertEquals(serija, jdbc.queryForObject("select serija from slajdovi where id = ?", String.class, s.path("id").asLong()));

        // pregled nosi isti sažetak, ceo slajd i grupaNaziv; bez SVG-ova
        JsonNode r = mcp.pozovi("prezentacija", mapa("prezentacijaId", prezentacija));
        assertFalse(McpTestKlijent.tekst(r).toLowerCase().contains("svg"), McpTestKlijent.tekst(r));
        JsonNode u = McpTestKlijent.json(r).path("slajdovi").path(0).path("zadatak");
        assertEquals("2 → 10", u.path("putanja").path(1).path("naziv").asString());
        assertFalse(u.has("grupaNaziv"));
        JsonNode ceo = uspeh("slajd", mapa("slajdId", s.path("id").asLong()));
        assertEquals("2 → 10", ceo.path("slajd").path("zadatak").path("grupaNaziv").asString());
    }

    @Test
    void zadatakStrogoNamenaIIskljucenje() {
        long koren = grupa(null, "Konverzije", "PREDAVANJE");
        long test = grupa(null, "Kolokvijum", "TEST");
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, zadatak("grupaId", koren, "x", 1)), "Nepoznato polje 'slajd.zadatak.x'.");
        ocekujGresku("dodaj_slajd", dodaj(prezentacija, zadatak("grupaId", test)),
                "Grupa mora biti iz banke ovog predmeta, namena Predavanja.");
        assertEquals(0, brojSlajdova(prezentacija));

        jdbc.update("insert into iskljucene_teme (predmet_id, opis, grupa_id, kreirano) values (?, 'Ne radi se', ?, now(6))",
                predmet.getId(), koren);
        JsonNode z = uspeh("dodaj_slajd", dodaj(prezentacija, zadatak("grupaId", koren))).path("slajd").path("zadatak");
        assertTrue(z.path("iskljucena").asBoolean());
        List<String> upozorenja = new ArrayList<>();
        z.path("upozorenja").forEach(n -> upozorenja.add(n.asString()));
        assertTrue(upozorenja.contains("Tema je isključena."), upozorenja.toString());
        assertEquals(1, brojSlajdova(prezentacija), "upozorenje nije blokada");
    }
}
