package tri.novica.gfssystem.rest.fajlovi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tri.novica.gfssystem.dto.uzivo.IzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.KomandaCmd;
import tri.novica.gfssystem.dto.uzivo.PokreniCmd;
import tri.novica.gfssystem.dto.uzivo.TipKomande;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.fajlovi.FajloviPodaci;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code GET /public/fajlovi/{kod}} i {@code /{kod}/{fajlId}} bez prijave kroz ceo lanac: spisak (samo pokazani slajdovi i fajlovi uz celu
 * prezentaciju), zaglavlja preuzimanja, 404 za nepokazan slajd, istekao i završen kod, 429 posle 40 pogrešnih po IP-u i da
 * ispravan kod ne troši pokušaje.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicFajloviRestIT {

    @Autowired MockMvc mvc;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired MedijService medijService;
    @Autowired JdbcTemplate jdbc;

    CasPodaci d;
    FajloviPodaci f;
    Long predavanje;
    Long uzPrvi;
    Long uzDrugi;
    Long uzSve;
    Long izvodjenje;
    String kod;
    String ip;

    @BeforeEach
    void setUp() {
        ip = "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "." + (1 + (int) (Math.random() * 250));
        d = new CasPodaci(jdbc);
        f = new FajloviPodaci(jdbc, medijService, prezentacijaService, d.predmetId);
        Long grupa = d.grupa(2026);
        predavanje = d.predavanje(grupa, LocalDate.now());
        Long prez = f.prezentacija("Javni " + UUID.randomUUID());
        Long s1 = f.slajd(prez, "Prvi");
        Long s2 = f.slajd(prez, "Drugi");
        uzPrvi = f.fajl(prez, s1, "prvi.docx");
        uzDrugi = f.fajl(prez, s2, "drugi.docx");
        uzSve = f.fajl(prez, null, "sve.docx");
        IzvodjenjeInfo iz = izvodjenjeService.pokreni(prez, new PokreniCmd(false, null, predavanje));
        izvodjenje = iz.id();
        izvodjenjeService.komanda(izvodjenje, new KomandaCmd(TipKomande.IDI_NA, 0));
        kod = jdbc.queryForObject("select aktivan_kod from kodovi_fajlova where predavanje_id = ?", String.class, predavanje);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from pokusaji where kljuc = ?", ip);
        d.obrisi();
        f.ocisti();
    }

    RequestPostProcessor sa() {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    @Test
    void spisakNosiSamoPokazaneISveUzPrezentaciju() throws Exception {
        mvc.perform(get("/public/fajlovi/" + kod).with(sa()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.kod").value(kod))
                .andExpect(jsonPath("$.fajlovi.length()").value(2))
                .andExpect(jsonPath("$.fajlovi[?(@.id==" + uzPrvi + ")].naziv").value("prvi.docx"))
                .andExpect(jsonPath("$.fajlovi[?(@.id==" + uzSve + ")].vrsta").value("DOCX"))
                .andExpect(jsonPath("$.fajlovi[?(@.id==" + uzDrugi + ")]").isEmpty());
    }

    @Test
    void preuzimanjeImaZaglavlja() throws Exception {
        mvc.perform(get("/public/fajlovi/" + kod + "/" + uzPrvi).with(sa()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("prvi.docx")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void nepokazanSlajdJeNotFoundIBezTrosenjaPokusaja() throws Exception {
        mvc.perform(get("/public/fajlovi/" + kod + "/" + uzDrugi).with(sa())).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("select count(*) from pokusaji where kljuc = ?", Integer.class, ip));
        izvodjenjeService.komanda(izvodjenje, new KomandaCmd(TipKomande.IDI_NA, 1));
        mvc.perform(get("/public/fajlovi/" + kod + "/" + uzDrugi).with(sa())).andExpect(status().isOk());
    }

    @Test
    void pogresanKodJeNotFoundSaPorukom() throws Exception {
        mvc.perform(get("/public/fajlovi/0000").with(sa()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("Kod ne važi. Proveri broj sa table."));
        mvc.perform(get("/public/fajlovi/abc").with(sa())).andExpect(status().isNotFound());
    }

    @Test
    void cetrdesetPogresnihDajeTooManyZaSvakiKod() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/public/fajlovi/" + kod).with(sa())).andExpect(status().isOk());   // ispravan kod ne troši
        }
        for (int i = 0; i < 40; i++) {
            mvc.perform(get("/public/fajlovi/0000").with(sa())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/public/fajlovi/0000").with(sa()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("Previše pogrešnih kodova. Pokušaj ponovo za 15 minuta."));
        mvc.perform(get("/public/fajlovi/" + kod).with(sa())).andExpect(status().isTooManyRequests());
        // drugi IP nije pogođen
        mvc.perform(get("/public/fajlovi/" + kod).with(r -> {
            r.setRemoteAddr("192.0.2.77");
            return r;
        })).andExpect(status().isOk());
    }

    @Test
    void zavrsenoPredavanjeIIstekaoKodSuNotFound() throws Exception {
        jdbc.update("update kodovi_fajlova set vazi_do = ? where predavanje_id = ?",
                java.time.LocalDateTime.now().minus(Duration.ofMinutes(1)), predavanje);
        mvc.perform(get("/public/fajlovi/" + kod).with(sa())).andExpect(status().isNotFound());
        jdbc.update("update kodovi_fajlova set vazi_do = ? where predavanje_id = ?",
                java.time.LocalDateTime.now().plus(Duration.ofHours(1)), predavanje);
        mvc.perform(get("/public/fajlovi/" + kod).with(sa())).andExpect(status().isOk());
        d.zavrsi(predavanje);
        mvc.perform(get("/public/fajlovi/" + kod).with(sa())).andExpect(status().isNotFound());
    }
}
