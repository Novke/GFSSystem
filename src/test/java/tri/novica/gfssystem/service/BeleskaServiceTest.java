package tri.novica.gfssystem.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tri.novica.gfssystem.dto.beleska.BeleskaInfo;
import tri.novica.gfssystem.dto.beleska.SaveBeleskaCmd;
import tri.novica.gfssystem.entity.Beleska;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.BeleskaRepository;
import tri.novica.gfssystem.repository.StudentRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BeleskaServiceTest {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDateTime SADA = LocalDateTime.of(2026, 10, 7, 12, 0);
    static final Clock CLOCK = Clock.fixed(SADA.atZone(ZONA).toInstant(), ZONA);

    @Mock BeleskaRepository beleskaRepository;
    @Mock StudentRepository studentRepository;

    BeleskaService service;
    Student student;

    @BeforeEach
    void setUp() {
        service = new BeleskaService(beleskaRepository, studentRepository, CLOCK);
        student = new Student();
        student.setId(7L);
    }

    private static SaveBeleskaCmd cmd(String tekst) {
        return new SaveBeleskaCmd(tekst);
    }

    @Test
    void kreiranjePostavljaKreiranoIBezIzmenjeno() {
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student));
        when(beleskaRepository.save(any(Beleska.class))).thenAnswer(inv -> {
            Beleska b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        BeleskaInfo info = service.create(7L, cmd("  Kasni na vežbe  "));

        assertEquals(1L, info.getId());
        assertEquals("Kasni na vežbe", info.getTekst());
        assertEquals(SADA, info.getKreirano());
        assertNull(info.getIzmenjeno());
    }

    @Test
    void izmenaPostavljaIzmenjenoAKreiranoOstaje() {
        LocalDateTime ranije = SADA.minusDays(3);
        Beleska b = new Beleska();
        b.setId(4L);
        b.setStudent(student);
        b.setTekst("staro");
        b.setKreirano(ranije);
        when(beleskaRepository.findById(4L)).thenReturn(Optional.of(b));
        when(beleskaRepository.save(any(Beleska.class))).thenAnswer(inv -> inv.getArgument(0));

        BeleskaInfo info = service.update(4L, cmd("novo"));

        assertEquals("novo", info.getTekst());
        assertEquals(ranije, info.getKreirano());
        assertEquals(SADA, info.getIzmenjeno());
    }

    @Test
    void nepostojeciStudentJe404NaKreiranjuIListi() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());
        when(studentRepository.existsById(99L)).thenReturn(false);

        SystemException e = assertThrows(SystemException.class, () -> service.create(99L, cmd("x")));
        assertEquals(404, e.getCode());
        assertEquals("Student ne postoji! ID = 99", e.getMessage());
        assertEquals(404, assertThrows(SystemException.class, () -> service.findByStudent(99L)).getCode());
        verify(beleskaRepository, never()).save(any());
    }

    @Test
    void nepostojecaBeleskaJe404NaIzmeniIBrisanju() {
        when(beleskaRepository.findById(5L)).thenReturn(Optional.empty());

        assertEquals(404, assertThrows(SystemException.class, () -> service.update(5L, cmd("x"))).getCode());
        SystemException e = assertThrows(SystemException.class, () -> service.delete(5L));
        assertEquals(404, e.getCode());
        assertEquals("Beleška ne postoji! ID = 5", e.getMessage());
        verify(beleskaRepository, never()).delete(any());
    }

    @Test
    void listaVracaRedosledIzRepozitorijuma() {
        Beleska nova = beleska(2L, SADA), stara = beleska(1L, SADA.minusDays(1));
        when(studentRepository.existsById(7L)).thenReturn(true);
        when(beleskaRepository.findByStudentIdOrderByKreiranoDescIdDesc(7L)).thenReturn(List.of(nova, stara));

        List<BeleskaInfo> lista = service.findByStudent(7L);

        assertEquals(List.of(2L, 1L), lista.stream().map(BeleskaInfo::getId).toList());
    }

    @Test
    void brisanjeBriseBelesku() {
        Beleska b = beleska(3L, SADA);
        when(beleskaRepository.findById(3L)).thenReturn(Optional.of(b));

        service.delete(3L);

        verify(beleskaRepository).delete(b);
    }

    @Test
    void validacijaOdbijaPrazanIPreDugTekst() {
        Validator v = Validation.buildDefaultValidatorFactory().getValidator();
        assertFalse(v.validate(cmd(null)).isEmpty());
        assertFalse(v.validate(cmd("")).isEmpty());
        assertFalse(v.validate(cmd("   ")).isEmpty());
        assertFalse(v.validate(cmd("a".repeat(2001))).isEmpty());
        assertTrue(v.validate(cmd("a".repeat(2000))).isEmpty());
        assertEquals("Tekst beleške je obavezan.", v.validate(cmd("")).iterator().next().getMessage());
    }

    private Beleska beleska(Long id, LocalDateTime kreirano) {
        Beleska b = new Beleska();
        b.setId(id);
        b.setStudent(student);
        b.setTekst("t" + id);
        b.setKreirano(kreirano);
        return b;
    }
}
