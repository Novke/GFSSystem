package tri.novica.gfssystem.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import tri.novica.gfssystem.dto.test.TestDetails;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.validation.TestPP;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Stariji studenti (ponavljaju predmet) smeju da se dodaju na test mlađe grupe; mlađi ne. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestServiceStarijiTest {

    @Mock TestRepository testRepository;
    @Mock TipTestaRepository tipTestaRepository;
    @Mock PredmetRepository predmetRepository;
    @Mock GrupaRepository grupaRepository;
    @Mock StudentRepository studentRepository;
    @Mock PolaganjeRepository polaganjeRepository;
    @Mock ModelMapper mapper;

    TestService service;

    @BeforeEach
    void setUp() {
        service = new TestService(testRepository, tipTestaRepository, predmetRepository, grupaRepository,
                studentRepository, polaganjeRepository, mapper, new TestPP());
        when(testRepository.save(any(tri.novica.gfssystem.entity.Test.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.map(any(), eq(TestDetails.class))).thenReturn(new TestDetails());
    }

    static Grupa grupa(long id, String naziv, Integer godina) {
        Grupa g = new Grupa();
        g.setId(id);
        g.setNaziv(naziv);
        g.setGodinaUpisa(godina);
        return g;
    }

    tri.novica.gfssystem.entity.Test test(Grupa g) {
        var t = new tri.novica.gfssystem.entity.Test();
        t.setId(10L);
        t.setGrupa(g);
        when(testRepository.findById(10L)).thenReturn(Optional.of(t));
        return t;
    }

    Student student(Grupa g) {
        Student s = new Student();
        s.setId(20L);
        s.setIndeks("GD7/22");
        s.setGrupa(g);
        when(studentRepository.findById(20L)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void studentIzIsteGrupeSeDodaje() {
        Grupa g = grupa(1, "GD-2025", 2025);
        var t = test(g);
        student(g);
        service.dodajIspitanika(10L, 20L);
        assertEquals(1, t.getPolaganja().size());
    }

    @Test
    void studentIzStarijeGrupeSeDodaje() {
        var t = test(grupa(2, "GD-2025", 2025));
        student(grupa(1, "GD-2024", 2024));
        service.dodajIspitanika(10L, 20L);
        assertEquals(1, t.getPolaganja().size());
    }

    @Test
    void studentIzMladjeGrupeSeOdbijaPriDodavanju() {
        var t = test(grupa(2, "GD-2025", 2025));
        student(grupa(3, "GD-2026", 2026));
        var ex = assertThrows(SystemException.class, () -> service.dodajIspitanika(10L, 20L));
        assertEquals("Student GD7/22 ne pripada grupi GD-2025", ex.getMessage());
        assertEquals(400, ex.getCode());
        assertTrue(t.getPolaganja().isEmpty());
        verify(testRepository, never()).save(any());
    }

    @Test
    void studentBezGrupeSeOdbija() {
        test(grupa(2, "GD-2025", 2025));
        student(null);
        assertThrows(SystemException.class, () -> service.dodajIspitanika(10L, 20L));
    }
}
