package tri.novica.gfssystem.rest.prijava;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.rest.uzivo.PublicUzivoRest;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;
import tri.novica.gfssystem.service.prijava.TestStudenti;
import tri.novica.gfssystem.service.uzivo.ImenaUcesnika;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Šav prijave i slajda Zadatak kraj na kraj, kroz ceo bezbednosni lanac nad pravom MySQL bazom: u ovom buildu je
 * {@code IdentitetStudenta} = {@code NalogIdentitet}, pa nastavnik sme da pokrene bodovan run (nema 409 {@code Prijava
 * studenata još nije dostupna.}); student sa važećim {@code gfs_student} ulazi kao on sam (ukucano ime se ignoriše), isti
 * student sa drugog telefona (drugi uređaj) je isti učesnik, bez kolačića (i kao nastavnik) bodovan run je 403
 * {@code Potrebna je prijava studenta.}, a nebodovan run i dalje radi bez prijave.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BodovanoUzivoIT {

    static final String POTREBNA_PRIJAVA = "Potrebna je prijava studenta.";

    @Autowired MockMvc mvc;
    static final JsonMapper json = JsonMapper.builder().build();
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired StudentskaPrijava prijava;
    @Autowired StudentRepository studentRepository;
    @Autowired PredmetRepository predmetRepository;
    @Autowired GrupaRepository grupaRepository;
    @Autowired PredavanjeRepository predavanjeRepository;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired JdbcTemplate jdbc;

    TestStudenti studenti;
    String nastavnickiKolacic;
    RequestPostProcessor nastavnik;
    Predmet predmet;
    Grupa grupa;
    Predavanje predavanje;
    final List<Long> prezentacije = new ArrayList<>();

    @BeforeEach
    void setUp() {
        studenti = new TestStudenti(studentRepository, jdbc);
        nastavnickiKolacic = PrijavaTestUtil.nastavnickiKolacic(nastavnici);
        nastavnik = PrijavaTestUtil.kaoNastavnik(nastavnickiKolacic);
        String oznaka = UUID.randomUUID().toString().substring(0, 8);
        predmet = new Predmet();
        predmet.setNaziv("Bodovano uživo " + oznaka);
        predmet = predmetRepository.save(predmet);
        grupa = new Grupa();
        grupa.setNaziv("BU-" + oznaka);
        grupa.setGodinaUpisa(2025);
        grupa = grupaRepository.save(grupa);
        Predavanje pr = new Predavanje();
        pr.setPredmet(predmet);
        pr.setGrupa(grupa);
        pr.setRb(1);
        pr.setDatum(LocalDate.of(2026, 10, 20));
        pr.setTema("Tema 1");
        pr.setZavrseno(false);
        predavanje = predavanjeRepository.save(pr);
    }

    @AfterEach
    void ocisti() {
        for (Long id : prezentacije) {
            jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", id);
            try {
                prezentacijaService.obrisi(id);   // izvođenja i učesnici kaskadom
            } catch (SystemException e) {
                // već obrisana
            }
        }
        predavanjeRepository.deleteById(predavanje.getId());
        grupaRepository.deleteById(grupa.getId());
        predmetRepository.deleteById(predmet.getId());
        studenti.obrisi();
    }

    // ---------------------------------------------------------------- pomoćnici

    /** Nova prezentacija i izvođenje kroz nastavnički API; vraća kod izvođenja. */
    String pokreni(boolean boduje) throws Exception {
        MvcResult p = mvc.perform(post("/prezentacije").with(nastavnik).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predmetId\":" + predmet.getId() + ",\"naziv\":\"Bodovano\"}"))
                .andExpect(status().isCreated()).andReturn();
        long prezentacijaId = json.readTree(p.getResponse().getContentAsString()).get("id").asLong();
        prezentacije.add(prezentacijaId);
        String cmd = boduje
                ? "{\"cuvanje\":true,\"predavanjeId\":" + predavanje.getId() + ",\"boduje\":true}"
                : "{\"cuvanje\":false}";
        MvcResult iz = mvc.perform(post("/prezentacije/{id}/izvodjenja", prezentacijaId).with(nastavnik)
                        .contentType(MediaType.APPLICATION_JSON).content(cmd))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.boduje").value(boduje))
                .andReturn();
        return json.readTree(iz.getResponse().getContentAsString()).get("kod").asText();
    }

    /** Nov uređaj studenta (kao prijava kodom na jednom telefonu); vraća kolačić {@code gfs_student}. */
    Cookie telefon(Student s) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        prijava.prijavi(s.getId(), NacinPrijave.KOD, new MockHttpServletRequest(), res);
        String h = res.getHeader(HttpHeaders.SET_COOKIE);
        return new Cookie(Kolacici.STUDENT, h.substring((Kolacici.STUDENT + "=").length(), h.indexOf(';')));
    }

    static MockHttpServletRequestBuilder ulaz(String kod, String ime) {
        return post("/public/uzivo/{kod}/prijava", kod)
                .header(HttpHeaders.ORIGIN, PrijavaTestUtil.ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ime\":\"" + ime + "\"}");
    }

    JsonNode telo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    int ucesnika(String kod) {
        return jdbc.queryForObject("select count(*) from ucesnici u join izvodjenja i on i.id = u.izvodjenje_id "
                + "where i.kod = ?", Integer.class, kod);
    }

    // ---------------------------------------------------------------- testovi

    @Test
    void bodovanRunSaNalogomUzimaStudentaIzKolacicaIstiStudentJeIstiUcesnik() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String kod = pokreni(true);

        Cookie prvi = telefon(s);
        mvc.perform(get("/public/uzivo/{kod}", kod).cookie(prvi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prijavaObavezna").value(true))
                .andExpect(jsonPath("$.prijavljenStudent").value(true));

        MvcResult r1 = mvc.perform(ulaz(kod, "Lažno ime").cookie(prvi))
                .andExpect(status().isCreated()).andReturn();
        JsonNode u1 = telo(r1);
        assertEquals(ImenaUcesnika.imeStudenta(s), u1.get("ime").asText(), "ime iz baze, ukucano se ignoriše");
        long ucesnikId = u1.get("ucesnikId").asLong();
        assertEquals(s.getId(), jdbc.queryForObject("select student_id from ucesnici where id = ?", Long.class, ucesnikId));
        assertNotNull(r1.getResponse().getCookie(PublicUzivoRest.KOLACIC));

        // drugi telefon istog studenta: drugi uređaj, isti učesnik (nov token), bez novog reda
        Cookie drugi = telefon(s);
        assertNotEquals(prvi.getValue(), drugi.getValue());
        MvcResult r2 = mvc.perform(ulaz(kod, "Neko drugi").cookie(drugi))
                .andExpect(status().isCreated()).andReturn();
        assertEquals(ucesnikId, telo(r2).get("ucesnikId").asLong());
        assertEquals(1, ucesnika(kod));
        String token1 = r1.getResponse().getCookie(PublicUzivoRest.KOLACIC).getValue();
        String token2 = r2.getResponse().getCookie(PublicUzivoRest.KOLACIC).getValue();
        assertNotEquals(token1, token2);
        mvc.perform(get("/public/uzivo/{kod}/ja", kod).cookie(new Cookie(PublicUzivoRest.KOLACIC, token2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ucesnikId").value(ucesnikId));
        mvc.perform(get("/public/uzivo/{kod}/ja", kod).cookie(new Cookie(PublicUzivoRest.KOLACIC, token1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void bodovanRunBezNalogaJe403() throws Exception {
        String kod = pokreni(true);
        mvc.perform(get("/public/uzivo/{kod}", kod))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prijavaObavezna").value(true))
                .andExpect(jsonPath("$.prijavljenStudent").value(false));
        mvc.perform(ulaz(kod, "Ana"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value(POTREBNA_PRIJAVA));
        assertEquals(0, ucesnika(kod));
    }

    /** Opozvan uređaj nije prijava: isto kao bez kolačića. */
    @Test
    void bodovanRunSaOpozvanimUredjajemJe403() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        Cookie c = telefon(s);
        prijava.odjavi(c.getValue());
        String kod = pokreni(true);
        mvc.perform(ulaz(kod, "Ana").cookie(c))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value(POTREBNA_PRIJAVA));
        assertEquals(0, ucesnika(kod));
    }

    /** Nastavnik ima prednost i nikad nije student (spec 2.2): i uz važeći studentski kolačić bodovan run je 403. */
    @Test
    void nastavnikNijeStudentUBodovanomRunu() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        String kod = pokreni(true);
        mvc.perform(ulaz(kod, "Ana").with(nastavnik).cookie(telefon(s)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value(POTREBNA_PRIJAVA));
        assertEquals(0, ucesnika(kod));
    }

    @Test
    void nebodovanRunRadiBezPrijave() throws Exception {
        String kod = pokreni(false);
        mvc.perform(get("/public/uzivo/{kod}", kod))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prijavaObavezna").value(false));
        mvc.perform(ulaz(kod, "Ana"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ime").value("Ana"));
        assertNull(jdbc.queryForObject("select u.student_id from ucesnici u join izvodjenja i on i.id = u.izvodjenje_id "
                + "where i.kod = ?", Long.class, kod));
    }
}
