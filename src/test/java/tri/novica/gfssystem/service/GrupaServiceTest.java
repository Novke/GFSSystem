package tri.novica.gfssystem.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import tri.novica.gfssystem.dto.grupa.CreateGrupaCmd;
import tri.novica.gfssystem.dto.grupa.KontaktiFajl;
import tri.novica.gfssystem.dto.grupa.WaLinkCmd;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.StudentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrupaServiceTest {

    @Mock GrupaRepository grupaRepository;
    @Mock StudentRepository studentRepository;

    GrupaService service;

    @BeforeEach
    void setUp() {
        service = new GrupaService(grupaRepository, studentRepository, new ModelMapper());
        when(grupaRepository.save(any(Grupa.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void grupaSaPostojecimNazivomSeOdbija() {
        when(grupaRepository.existsByNazivIgnoreCase("TEST-2026")).thenReturn(true);
        SystemException ex = assertThrows(SystemException.class,
                () -> service.save(new CreateGrupaCmd("  TEST-2026 ", 2026)));
        assertEquals(400, ex.getCode());
        assertEquals("Grupa sa nazivom TEST-2026 već postoji.", ex.getMessage());
        verify(grupaRepository, never()).save(any());
    }

    @Test
    void nazivSeCuvaBezRazmakaNaKrajevima() {
        assertEquals("TEST-2026", service.save(new CreateGrupaCmd("  TEST-2026 ", 2026)).getNaziv());
    }

    @Test
    void listaGrupaImaBrojStudenata() {
        Grupa g = new Grupa();
        g.setId(5L);
        g.setNaziv("TEST-2026");
        when(grupaRepository.findAll()).thenReturn(List.of(g));
        when(studentRepository.countByGrupaId(5L)).thenReturn(3L);
        assertEquals(3L, service.findAll().get(0).getBrojStudenata());
    }

    private Grupa grupa(long id, String naziv) {
        Grupa g = new Grupa();
        g.setId(id);
        g.setNaziv(naziv);
        g.setStudenti(new ArrayList<>());
        when(grupaRepository.findById(id)).thenReturn(Optional.of(g));
        when(grupaRepository.findByIdFetchStudents(id)).thenReturn(Optional.of(g));
        return g;
    }

    private static Student student(long id, String ime, String prezime, String indeks, String telefon, String email) {
        Student s = new Student();
        s.setId(id);
        s.setIme(ime);
        s.setPrezime(prezime);
        s.setIndeks(indeks);
        s.setBrojTelefona(telefon);
        s.setEmail(email);
        return s;
    }

    @Test
    void waLinkSeCuvaUKanonskomObliku() {
        Grupa g = grupa(5L, "GD-2026");
        assertEquals("https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl",
                service.postaviWaLink(5L, new WaLinkCmd(" chat.whatsapp.com/AbCdEfGhIj1234567890Kl?mode=x ")).waLink());
        assertEquals("https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl", g.getWaLink());
    }

    @Test
    void prazanWaLinkUklanjaLink() {
        Grupa g = grupa(5L, "GD-2026");
        g.setWaLink("https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl");
        assertNull(service.postaviWaLink(5L, new WaLinkCmd("")).waLink());
        assertNull(g.getWaLink());
    }

    @Test
    void neispravanWaLinkSeNeCuva() {
        Grupa g = grupa(5L, "GD-2026");
        g.setWaLink("https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl");
        SystemException ex = assertThrows(SystemException.class, () -> service.postaviWaLink(5L, new WaLinkCmd("https://evil.example/x")));
        assertEquals(400, ex.getCode());
        assertEquals("https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl", g.getWaLink());
        verify(grupaRepository, never()).save(any());
    }

    @Test
    void waLinkNepostojeceGrupeJe404() {
        when(grupaRepository.findById(9L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(SystemException.class, () -> service.postaviWaLink(9L, new WaLinkCmd(null))).getCode());
    }

    @Test
    void kontaktiPoIndeksuSaMedjunarodnimBrojemBezStudenataBezKontakta() {
        Grupa g = grupa(5L, "GD-2026");
        g.getStudenti().addAll(List.of(
                student(1L, "Mila", "Jović", "GD12", "064 123 4567", null),
                student(2L, "Ana", "Petrović", "GD3", "+381 65 111 2222", "ana@example.com"),
                student(3L, "Bez", "Kontakta", "GD1", " ", null),
                student(4L, "Samo", "Email", "GD20", null, "samo@example.com")));
        KontaktiFajl f = service.kontakti(5L);
        assertEquals("kontakti-GD-2026.vcf", f.imeFajla());
        String v = f.sadrzaj();
        assertFalse(v.contains("Kontakta"));
        assertTrue(v.indexOf("Petrović") < v.indexOf("Jović") && v.indexOf("Jović") < v.indexOf("Samo"), v);
        assertTrue(v.contains("TEL;TYPE=CELL:+381641234567\r\n"));
        assertTrue(v.contains("TEL;TYPE=CELL:+381651112222\r\n"));
        assertTrue(v.contains("NOTE:GFS GD-2026\\, indeks GD3\r\n"), v);
        assertEquals(3, v.split("BEGIN:VCARD", -1).length - 1);
    }

    @Test
    void imeFajlaBezZnakovaKojiSmetajuZaglavlju() {
        grupa(5L, "Prva godina \"A\" / 2026");
        assertEquals("kontakti-Prva-godina-A-2026.vcf", service.kontakti(5L).imeFajla());
        grupa(6L, "Ćirilica šđ");
        assertEquals("kontakti-Cirilica-sdj.vcf", service.kontakti(6L).imeFajla());
    }
}
