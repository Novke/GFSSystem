package tri.novica.gfssystem.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import tri.novica.gfssystem.dto.student.CreateStudentCmd;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Polaganje;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Ručni unos studenta prolazi istu normalizaciju i dedupe kao onboarding (spec 10.1.2). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock GrupaRepository grupaRepository;
    @Mock PredmetRepository predmetRepository;
    @Mock AktivnostRepository aktivnostRepository;
    @Mock DomaciRepository domaciRepository;
    @Mock PolaganjeRepository polaganjeRepository;

    StudentService service;
    Grupa grupa;

    @BeforeEach
    void setUp() {
        ModelMapper mapper = new ModelMapper();   // isto podešavanje kao bean u GfsSystemApplication
        mapper.getConfiguration()
                .setPropertyCondition(context -> context.getSource() != null)
                .setSkipNullEnabled(true)
                .setMatchingStrategy(MatchingStrategies.STRICT);
        service = new StudentService(studentRepository, grupaRepository, predmetRepository, aktivnostRepository,
                domaciRepository, polaganjeRepository, mapper);
        grupa = new Grupa();
        grupa.setId(5L);
        when(grupaRepository.findById(5L)).thenReturn(Optional.of(grupa));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    static CreateStudentCmd cmd(String indeks, int godina, String email) {
        return new CreateStudentCmd(5L, "Ana", "Anić", godina, indeks, "064123456", email, null, null);
    }

    static Student student(String indeks) {
        Student s = new Student();
        s.setIndeks(indeks);
        return s;
    }

    @Test
    void postojeciStudentSaIstimIndeksomIGodinomSeOdbija() {
        when(studentRepository.postojiStudent("GD12", 2026)).thenReturn(true);
        SystemException ex = assertThrows(SystemException.class, () -> service.create(cmd(" gd 12", 2026, null)));
        assertEquals(400, ex.getCode());
        assertEquals(StudentService.DUPLIKAT, ex.getMessage());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void cuvaNormalizovanIndeksIEmail() {
        service.create(cmd("gd 12", 2026, "  Ana@Example.COM "));
        ArgumentCaptor<Student> c = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(c.capture());
        assertEquals("GD12", c.getValue().getIndeks());
        assertEquals("ana@example.com", c.getValue().getEmail());
        assertSame(grupa, c.getValue().getGrupa());
        assertNull(service.create(cmd("GD13", 2026, "   ")).getEmail());
    }

    @Test
    void studentiGrupeSuSortiraniPoBrojuIndeksa() {
        when(studentRepository.findByGrupa(grupa)).thenReturn(List.of(student("GD12"), student("GD5"), student("GD100")));
        assertEquals(List.of("GD5", "GD12", "GD100"),
                service.findAllByGroup(5L).stream().map(StudentInfo::getIndeks).toList());
    }

    // ---- pregled studenta: prag i prolaz sa servera (pravilo Prolaz), stari sačuvani polozio se ne izlaže

    private static Polaganje polaganje(long id, Integer prag, Double poeni, boolean prepisivao, Boolean polozio) {
        var t = new tri.novica.gfssystem.entity.Test();
        t.setId(100 + id);
        t.setDatum(java.time.LocalDate.of(2025, 11, (int) id));
        t.setPragProlaza(prag);
        Polaganje p = Polaganje.defaultPolaganje(t, null);
        p.setId(id);
        p.setOstvareniPoeni(poeni);
        p.setPrepisivao(prepisivao);
        p.setPolozio(polozio);
        return p;
    }

    @Test
    void pregledStudentaIzlazePragIProlazSaServera() {
        Student s = student("GD12");
        s.setGrupa(grupa);
        s.setAktivnosti(new java.util.HashSet<>());
        s.setUradjeniDomaci(new java.util.HashSet<>());
        s.setPolaganja(new java.util.HashSet<>(List.of(
                polaganje(1, 20, 25.0, false, null),    // položio
                polaganje(2, 20, 10.0, false, true),    // pao, iako je stari polozio = true
                polaganje(3, 20, 40.0, true, null),     // prepisivao: pao
                polaganje(4, null, 30.0, false, true))));   // test bez praga: nema prolaza
        when(studentRepository.findByIdFetchDetails(9L)).thenReturn(Optional.of(s));

        var po = service.findById(9L).getPolaganja().stream()
                .collect(java.util.stream.Collectors.toMap(i -> i.getId(), i -> i));
        assertEquals(Boolean.TRUE, po.get(1L).getPolozeno());
        assertEquals(20, po.get(1L).getPragProlaza());
        assertEquals(Boolean.FALSE, po.get(2L).getPolozeno());
        assertEquals(Boolean.FALSE, po.get(3L).getPolozeno());
        assertTrue(po.get(3L).isPrepisivao());
        assertNull(po.get(4L).getPolozeno());
        assertNull(po.get(4L).getPragProlaza());
    }
}
