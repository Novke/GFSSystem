package tri.novica.gfssystem.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import tri.novica.gfssystem.dto.predavanje.PredavanjeDetails;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Stariji studenti (ponavljaju predmet) smeju na predavanje mlađe grupe; mlađi ne. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PredavanjeServiceStarijiTest {

    @Mock PredavanjeRepository predavanjeRepository;
    @Mock PredmetRepository predmetRepository;
    @Mock GrupaRepository grupaRepository;
    @Mock StudentRepository studentRepository;
    @Mock AktivnostRepository aktivnostRepository;
    @Mock ModelMapper mapper;

    PredavanjeService service;

    @BeforeEach
    void setUp() {
        service = new PredavanjeService(predavanjeRepository, predmetRepository, grupaRepository, studentRepository,
                aktivnostRepository, mapper);
        when(predavanjeRepository.save(any(Predavanje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.map(any(), eq(PredavanjeDetails.class))).thenReturn(new PredavanjeDetails());
    }

    static Grupa grupa(long id, String naziv, Integer godina) {
        Grupa g = new Grupa();
        g.setId(id);
        g.setNaziv(naziv);
        g.setGodinaUpisa(godina);
        return g;
    }

    Predavanje predavanje(Grupa g) {
        Predavanje p = new Predavanje();
        p.setId(10L);
        p.setGrupa(g);
        when(predavanjeRepository.findById(10L)).thenReturn(Optional.of(p));
        return p;
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
        Predavanje p = predavanje(g);
        student(g);
        service.dodajPrisutnog(10L, 20L);
        assertEquals(1, p.getAktivnosti().size());
    }

    @Test
    void studentIzStarijeGrupeSeDodaje() {
        Predavanje p = predavanje(grupa(2, "GD-2025", 2025));
        student(grupa(1, "GD-2024", 2024));
        service.dodajPrisutnog(10L, 20L);
        assertEquals(1, p.getAktivnosti().size());
    }

    @Test
    void studentIzMladjeGrupeSeOdbija() {
        Predavanje p = predavanje(grupa(2, "GD-2025", 2025));
        student(grupa(3, "GD-2026", 2026));
        var ex = assertThrows(SystemException.class, () -> service.dodajPrisutnog(10L, 20L));
        assertEquals("Student GD7/22 ne pripada grupi GD-2025", ex.getMessage());
        assertEquals(400, ex.getCode());
        assertTrue(p.getAktivnosti().isEmpty());
        verify(predavanjeRepository, never()).save(any());
    }

    @Test
    void studentBezGrupeSeOdbijaNaPredavanjuSaGrupom() {
        predavanje(grupa(2, "GD-2025", 2025));
        student(null);
        assertThrows(SystemException.class, () -> service.dodajPrisutnog(10L, 20L));
    }

    @Test
    void predavanjeBezGrupeDozvoljavaSvakoga() {
        Predavanje p = predavanje(null);
        student(grupa(3, "GD-2026", 2026));
        service.dodajPrisutnog(10L, 20L);
        assertEquals(1, p.getAktivnosti().size());
    }
}
