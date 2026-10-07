package tri.novica.gfssystem.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.grupa.UpdateGrupaCmd;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.dto.student.UpdateStudentCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.dto.test.tip.UpdateTipTestaCmd;
import tri.novica.gfssystem.service.GrupaService;
import tri.novica.gfssystem.service.StudentService;
import tri.novica.gfssystem.service.TestService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PUT /grupe/{id}, /studenti/{id}, /test/tip/{id}: validacija (poruke na srpskom) i povezivanje sa servisom. */
@WebMvcTest({GrupaRest.class, StudentRest.class, TestRest.class})
class IzmeneRestTest {

    @Autowired MockMvc mvc;
    @MockitoBean GrupaService grupe;
    @MockitoBean StudentService studenti;
    @MockitoBean TestService testovi;

    static final String STUDENT_OK = """
            {"grupaId":5,"ime":"Ana","prezime":"Anić","indeks":"GD12","godina":2025}""";

    private org.springframework.test.web.servlet.ResultActions putJson(String url, String body) throws Exception {
        return mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---- grupa

    @Test
    void grupaIzmenaVracaGrupuInfo() throws Exception {
        when(grupe.update(eq(5L), any(UpdateGrupaCmd.class))).thenReturn(new GrupaInfo(5L, "GD-2025", 2025, 2L));
        putJson("/grupe/5", "{\"naziv\":\"GD-2025\",\"godinaUpisa\":2025}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.naziv").value("GD-2025"))
                .andExpect(jsonPath("$.brojStudenata").value(2));
    }

    @Test
    void grupaBezGodineIliSaPraznimNazivomJe400() throws Exception {
        putJson("/grupe/5", "{\"naziv\":\"GD\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Godina upisa je obavezna."));
        putJson("/grupe/5", "{\"naziv\":\" \",\"godinaUpisa\":2025}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Naziv grupe je obavezan."));
        putJson("/grupe/5", "{\"naziv\":\"GD\",\"godinaUpisa\":1999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Godina upisa nije ispravna."));
        verify(grupe, never()).update(any(), any());
    }

    // ---- student

    @Test
    void studentIzmenaVracaStudentInfo() throws Exception {
        StudentInfo info = new StudentInfo();
        info.setId(11L);
        info.setIndeks("GD12");
        when(studenti.update(eq(11L), any(UpdateStudentCmd.class))).thenReturn(info);
        putJson("/studenti/11", STUDENT_OK)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.indeks").value("GD12"));
    }

    @Test
    void studentBezGrupeIGodineJe400() throws Exception {
        putJson("/studenti/11", "{\"ime\":\"Ana\",\"prezime\":\"Anić\",\"indeks\":\"GD12\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("Grupa je obavezna.")))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("Godina upisa je obavezna.")));
        verify(studenti, never()).update(any(), any());
    }

    @Test
    void studentNeispravnaPoljaDajuPorukeNaSrpskom() throws Exception {
        putJson("/studenti/11", "{\"grupaId\":5,\"ime\":\"\",\"prezime\":\"A\",\"indeks\":\"G\",\"godina\":2025}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("Ime je obavezno.")))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("Indeks mora imati od 2 do 20 znakova.")));
        putJson("/studenti/11", "{\"grupaId\":5,\"ime\":\"Ana\",\"prezime\":\"Anić\",\"indeks\":\"GD12\",\"godina\":2101}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Godina upisa nije ispravna."));
        putJson("/studenti/11", "{\"grupaId\":5,\"ime\":\"Ana\",\"prezime\":\"Anić\",\"indeks\":\"GD12\",\"godina\":2025,\"email\":\"nije-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Email nije ispravan."));
        putJson("/studenti/11", "{\"grupaId\":5,\"ime\":\"Ana\",\"prezime\":\"Anić\",\"indeks\":\"GD12\",\"godina\":2025,\"brojTelefona\":\"012345678901234567890\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Broj telefona može imati najviše 20 znakova."));
        putJson("/studenti/11", "{\"grupaId\":5,\"ime\":\"Ana\",\"prezime\":\"Anić\",\"indeks\":\"GD12\",\"godina\":2025,\"opstina\":\"" + "x".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Opština može imati najviše 100 znakova."));
        verify(studenti, never()).update(any(), any());
    }

    @Test
    void studentEmailDuziOd255Je400() throws Exception {
        String email = "a".repeat(250) + "@b.rs";
        putJson("/studenti/11", "{\"grupaId\":5,\"ime\":\"Ana\",\"prezime\":\"Anić\",\"indeks\":\"GD12\",\"godina\":2025,\"email\":\"" + email + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("Email")));
    }

    // ---- tip testa

    @Test
    void tipTestaIzmenaVracaTipInfoSaAktivnoscu() throws Exception {
        when(testovi.updateTipTesta(eq(4L), any(UpdateTipTestaCmd.class))).thenReturn(new TipTestaInfo(4L, "Kolokvijum", false));
        putJson("/test/tip/4", "{\"naziv\":\"Kolokvijum\",\"aktivan\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.naziv").value("Kolokvijum"))
                .andExpect(jsonPath("$.aktivan").value(false));
    }

    @Test
    void tipTestaBezAktivnostiIliSaPrevelikimNazivomJe400() throws Exception {
        putJson("/test/tip/4", "{\"naziv\":\"Kolokvijum\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Aktivnost tipa testa je obavezna."));
        putJson("/test/tip/4", "{\"naziv\":\"" + "x".repeat(61) + "\",\"aktivan\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Naziv tipa testa može imati najviše 60 znakova."));
        putJson("/test/tip/4", "{\"naziv\":\"\",\"aktivan\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Naziv tipa testa je obavezan."));
        verify(testovi, never()).updateTipTesta(any(), any());
    }
}
