package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Alati odgovora sa časa kroz pravi {@code /mcp}: {@code izvodjenja_prezentacije}, {@code odgovori_runde} i
 * {@code preoceni_odgovore} (samo završeno izvođenje, id iz runde, anketa nema ocenu, trag u {@code preocene_odgovora},
 * ponovljen poziv bez efekta, povratak istim pozivom, polja zadatka i poeni takmičenja).
 */
class IzvodjenjaAlatiDbTest extends McpDbTestOsnova {

    static final AtomicInteger KOD = new AtomicInteger((int) (Math.random() * 400_000) + 500_000);
    static final String POLJA = "{\"a\":{\"vrednost\":\"5\",\"tacno\":false},\"b\":{\"vrednost\":\"7\",\"tacno\":true}}";

    @Autowired PrezentacijaService prezentacijaService;
    @Autowired tri.novica.gfssystem.service.uzivo.StanjeService stanjeService;

    Predmet predmet;
    String serija;
    Long prezentacija;
    Long izvodjenje;
    Long runda;

    @BeforeEach
    void setUp() {
        predmet = noviPredmet("Preocena MCP test");
        serija = "preocena-" + UUID.randomUUID().toString().substring(0, 8);
        prezentacija = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Vežbe 1", null, null)).id();
        izvodjenje = izvodjenje("ZAVRSENO", 0);
        runda = runda(izvodjenje);
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

    Long izvodjenje(String status, int takmicenje) {
        String kod = String.valueOf(KOD.incrementAndGet());
        jdbc.update("""
                insert into izvodjenja (prezentacija_id, kod, aktivan_kod, cuvanje, status, pocetak, prikaz, korak,
                  rezultati_prikazani, tacan_prikazan, rang_lista_prikazana, ekran, qr_prikazan, telefon_prikaz,
                  detalji_dozvoljeni, takmicenje, verzija)
                values (?, ?, ?, 1, ?, now(6), 'PRIJAVA', 0, 0, 0, 0, 'NORMALAN', 0, 'DUGMAD', 1, ?, 1)""",
                prezentacija, kod, "AKTIVNO".equals(status) ? kod : null, status, takmicenje);
        return jdbc.queryForObject("select id from izvodjenja where kod = ?", Long.class, kod);
    }

    Long runda(Long izvodjenjeId) {
        jdbc.update("insert into pitanje_runde (izvodjenje_id, redni_broj, snimak, otvoreno, trajanje_ms) "
                + "values (?, 1, '{}', now(6), 30000)", izvodjenjeId);
        return jdbc.queryForObject("select max(id) from pitanje_runde where izvodjenje_id = ?", Long.class, izvodjenjeId);
    }

    /** Odgovor učesnika {@code ime}; {@code tacno} je {@code null} za anketu, {@code polja} samo za zadatak. */
    Long odgovor(Long rundaId, Long izvodjenjeId, String ime, Boolean tacno, String tekst, String polja) {
        jdbc.update("insert into ucesnici (izvodjenje_id, ime, token_hash, izbacen, kreirano) values (?, ?, ?, 0, now(6))",
                izvodjenjeId, ime, UUID.randomUUID().toString().replace("-", "").repeat(2));
        Long ucesnik = jdbc.queryForObject("select max(id) from ucesnici where izvodjenje_id = ?", Long.class, izvodjenjeId);
        jdbc.update("insert into odgovori (runda_id, ucesnik_id, tekst, polja, tacno, poeni, vreme_ms, sakriven, kreirano) "
                + "values (?, ?, ?, ?, ?, 0, 3000, 0, now(6))", rundaId, ucesnik, tekst, polja, tacno);
        return jdbc.queryForObject("select id from odgovori where runda_id = ? and ucesnik_id = ?", Long.class, rundaId, ucesnik);
    }

    Boolean tacno(Long odgovorId) {
        return jdbc.queryForObject("select tacno from odgovori where id = ?", Boolean.class, odgovorId);
    }

    long tragova(Long odgovorId) {
        return jdbc.queryForObject("select count(*) from preocene_odgovora where odgovor_id = ?", Long.class, odgovorId);
    }

    Map<String, Object> preoceni(Long rundaId, List<Long> ids, boolean tacno, Object... dodatno) {
        Map<String, Object> m = mapa("serija", serija, "rundaId", rundaId, "odgovori", ids, "tacno", tacno, "razlog",
                "Telefon nije imao minus.");
        m.putAll(mapa(dodatno));
        return m;
    }

    @Test
    void citanjeDajeRundeIOdgovoreKaoPodatak() {
        Long o = odgovor(runda, izvodjenje, "Ana Anić", false, "5", null);
        odgovor(runda, izvodjenje, "Pera", true, "-5", null);

        JsonNode izvodjenja = uspeh("izvodjenja_prezentacije", mapa("prezentacijaId", prezentacija));
        JsonNode r = izvodjenja.get(0).path("runde").get(0);
        assertEquals(runda, r.path("id").asLong(), izvodjenja.toString());
        assertEquals(2, r.path("brojOdgovora").asInt());
        assertEquals(1, r.path("brojTacnih").asInt());

        JsonNode odgovori = uspeh("odgovori_runde", mapa("rundaId", runda));
        assertEquals(2, odgovori.size());
        assertEquals(o, odgovori.get(0).path("id").asLong());
        assertEquals("Ana Anić", odgovori.get(0).path("ime").asString());
        assertEquals("5", odgovori.get(0).path("vrednost").asString());
        assertFalse(odgovori.get(0).path("tacno").asBoolean());

        ocekujGresku("odgovori_runde", mapa("rundaId", Long.MAX_VALUE), "Runda nije pronađena.");
    }

    @Test
    void preocenaMenjaOcenuOstavljaTragIVracaSe() {
        Long o = odgovor(runda, izvodjenje, "Ana", false, "5", null);
        Long drugi = odgovor(runda, izvodjenje, "Pera", true, "-5", null);

        JsonNode r = uspeh("preoceni_odgovore", preoceni(runda, List.of(o, drugi), true));
        assertEquals(1, r.path("izmenjeni").size(), r.toString());
        assertEquals(o, r.path("izmenjeni").get(0).asLong());
        assertEquals(2, r.path("brojTacnih").asInt());
        assertTrue(tacno(o));
        assertEquals(1, tragova(o));
        assertEquals(0, tragova(drugi));

        // ponovljen poziv nema efekta
        assertEquals(0, uspeh("preoceni_odgovore", preoceni(runda, List.of(o), true)).path("izmenjeni").size());
        assertEquals(1, tragova(o));
    }

    @Test
    void vratiPreocenuVracaTacnoStaroStanje() {
        Long takmicarsko = izvodjenje("ZAVRSENO", 1);
        Long r = runda(takmicarsko);
        Long o = odgovor(r, takmicarsko, "Ana", false, null, POLJA);
        Long vecTacan = odgovor(r, takmicarsko, "Pera", true, null, "{\"a\":{\"vrednost\":\"-5\",\"tacno\":true}}");
        jdbc.update("update odgovori set poeni = 833 where id = ?", vecTacan);
        jdbc.update("update odgovori set poeni = 17 where id = ?", o);

        uspeh("preoceni_odgovore", preoceni(r, List.of(o, vecTacan), true));
        assertTrue(tacno(o));
        uspeh("preoceni_odgovore", preoceni(r, List.of(o), true, "polje", "a"));

        JsonNode v = uspeh("vrati_preocenu", mapa("serija", serija + "-v", "rundaId", r, "ciljSerija", serija));
        assertEquals(1, v.path("izmenjeni").size(), v.toString());
        assertFalse(tacno(o));
        assertEquals(POLJA, jdbc.queryForObject("select polja from odgovori where id = ?", String.class, o));
        assertEquals(17, jdbc.queryForObject("select poeni from odgovori where id = ?", Integer.class, o));
        assertTrue(tacno(vecTacan));
        assertEquals(833, jdbc.queryForObject("select poeni from odgovori where id = ?", Integer.class, vecTacan));

        assertEquals(0, uspeh("vrati_preocenu", mapa("serija", serija + "-v", "rundaId", r, "ciljSerija", serija))
                .path("izmenjeni").size());
        ocekujGresku("vrati_preocenu", mapa("serija", serija, "rundaId", r, "ciljSerija", "nepostojeca-serija"),
                "Serija nema preocena u ovoj rundi.");
    }

    @Test
    void pregledIzvodjenjaPokazujePreocenu() {
        jdbc.update("update pitanje_runde set snimak = ? where id = ?", """
                {"tip":"BROJ","tekst":"x","vremeSekunde":30,"brojTacno":-5.0,"brojOdstupanje":0.0}""", runda);
        Long o = odgovor(runda, izvodjenje, "Ana", false, null, null);
        jdbc.update("update odgovori set broj = 5 where id = ?", o);
        jdbc.update("update izvodjenja set tacan_prikazan = 1 where id = ?", izvodjenje);
        assertEquals(0, tacnihUPregledu());

        uspeh("preoceni_odgovore", preoceni(runda, List.of(o), true));

        assertEquals(1, tacnihUPregledu());
    }

    int tacnihUPregledu() {
        return stanjeService.rezultati(izvodjenje).pitanja().getFirst().rezultat().tacnih();
    }

    @Test
    void preocenaZadatkaMenjaPoljeIUkupnoJeTacnoKadSuSvaTacna() {
        Long o = odgovor(runda, izvodjenje, "Ana", false, null, POLJA);

        uspeh("preoceni_odgovore", preoceni(runda, List.of(o), true, "polje", "a"));
        assertTrue(tacno(o));
        JsonNode polja = uspeh("odgovori_runde", mapa("rundaId", runda)).get(0);
        assertTrue(polja.path("vrednost").asString().contains("\"tacno\":true"), polja.toString());
        assertFalse(polja.path("vrednost").asString().contains("\"tacno\":false"), polja.toString());
        assertEquals(POLJA, jdbc.queryForObject("select stara_polja from preocene_odgovora where odgovor_id = ?", String.class, o));

        uspeh("preoceni_odgovore", preoceni(runda, List.of(o), false, "polje", "b"));
        assertFalse(tacno(o));

        ocekujGresku("preoceni_odgovore", preoceni(runda, List.of(o), true, "polje", "c"),
                "Odgovor " + o + " nema polje 'c'.");
    }

    @Test
    void takmicenjeDobijaPoeneTekPrekoPreocene() {
        Long takmicarsko = izvodjenje("ZAVRSENO", 1);
        Long r = runda(takmicarsko);
        Long o = odgovor(r, takmicarsko, "Ana", false, "5", null);
        uspeh("preoceni_odgovore", preoceni(r, List.of(o), true));
        assertTrue(jdbc.queryForObject("select poeni from odgovori where id = ?", Integer.class, o) > 0);
        uspeh("preoceni_odgovore", preoceni(r, List.of(o), false));
        assertEquals(0, jdbc.queryForObject("select poeni from odgovori where id = ?", Integer.class, o));
    }

    @Test
    void uToku409AGresniIdIAnketaNeMenjajuNista() {
        Long aktivno = izvodjenje("AKTIVNO", 0);
        Long r = runda(aktivno);
        Long uToku = odgovor(r, aktivno, "Ana", false, "5", null);
        ocekujGresku("preoceni_odgovore", preoceni(r, List.of(uToku), true), "Izvođenje je u toku; ocene se menjaju posle časa.");
        assertFalse(tacno(uToku));

        Long o = odgovor(runda, izvodjenje, "Pera", false, "5", null);
        Long anketa = odgovor(runda, izvodjenje, "Mika", null, "možda", null);
        ocekujGresku("preoceni_odgovore", preoceni(runda, List.of(o, uToku), true), "Odgovor " + uToku + " nije iz ove runde.");
        assertFalse(tacno(o));
        assertEquals(0, tragova(o));
        ocekujGresku("preoceni_odgovore", preoceni(runda, List.of(o, anketa), true),
                "Odgovor " + anketa + " nema tačnost: pitanje nema tačan odgovor.");
        assertFalse(tacno(o));
        assertEquals(0, tragova(o));

        ocekujGresku("preoceni_odgovore", preoceni(runda, List.of(), true), "Pošalji bar jedan odgovor.");
        ocekujGresku("preoceni_odgovore", preoceni(runda, List.of(o), true, "razlog", " "), "Razlog je obavezan (1 do 300 znakova).");
    }
}
