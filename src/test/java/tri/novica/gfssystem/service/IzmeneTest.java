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
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.grupa.UpdateGrupaCmd;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.dto.student.UpdateStudentCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.dto.test.tip.UpdateTipTestaCmd;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.TipTesta;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.validation.TestPP;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Izmena grupe, studenta (uključujući premeštanje) i tipa testa. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IzmeneTest {

    @Mock StudentRepository studentRepository;
    @Mock GrupaRepository grupaRepository;
    @Mock PredmetRepository predmetRepository;
    @Mock AktivnostRepository aktivnostRepository;
    @Mock DomaciRepository domaciRepository;
    @Mock PolaganjeRepository polaganjeRepository;
    @Mock TestRepository testRepository;
    @Mock TipTestaRepository tipTestaRepository;

    StudentService studentService;
    GrupaService grupaService;
    TestService testService;

    Grupa stara;
    Grupa nova;
    Student student;

    @BeforeEach
    void setUp() {
        ModelMapper mapper = new ModelMapper();   // isto podešavanje kao bean u GfsSystemApplication
        mapper.getConfiguration()
                .setPropertyCondition(context -> context.getSource() != null)
                .setSkipNullEnabled(true)
                .setMatchingStrategy(MatchingStrategies.STRICT);
        studentService = new StudentService(studentRepository, grupaRepository, predmetRepository,
                aktivnostRepository, domaciRepository, polaganjeRepository, mapper);
        grupaService = new GrupaService(grupaRepository, studentRepository, mapper);
        testService = new TestService(testRepository, tipTestaRepository, predmetRepository, grupaRepository,
                studentRepository, polaganjeRepository, mapper, new TestPP());

        stara = grupa(5L, "GD-2025", 2025);
        nova = grupa(6L, "GD-2026", 2026);
        when(grupaRepository.findById(5L)).thenReturn(Optional.of(stara));
        when(grupaRepository.findById(6L)).thenReturn(Optional.of(nova));

        student = new Student();
        student.setId(11L);
        student.setIme("Ana");
        student.setPrezime("Anić");
        student.setGodina(2025);
        student.setIndeks("GD12");
        student.setEmail("ana@example.com");
        student.setBrojTelefona("064123456");
        student.setOpstina("Subotica");
        student.setDatumRodjenja(LocalDate.of(2005, 3, 4));
        student.setGrupa(stara);
        when(studentRepository.findById(11L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(grupaRepository.save(any(Grupa.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tipTestaRepository.save(any(TipTesta.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    static Grupa grupa(long id, String naziv, int godina) {
        Grupa g = new Grupa();
        g.setId(id);
        g.setNaziv(naziv);
        g.setGodinaUpisa(godina);
        return g;
    }

    static UpdateStudentCmd studentCmd(long grupaId, String indeks, int godina) {
        return new UpdateStudentCmd(grupaId, "Ana", "Anić", indeks, godina, null, null, null, null);
    }

    // ---- student

    @Test
    void izmenaStudentaNaSopstveniIndeksIGodinuProlazi() {
        // postojiStudent bi našao samog sebe; zato izmena proverava samo DRUGE studente
        when(studentRepository.postojiStudent("GD12", 2025)).thenReturn(true);
        when(studentRepository.postojiDrugiStudent("GD12", 2025, 11L)).thenReturn(false);

        StudentInfo info = studentService.update(11L, studentCmd(5L, " gd 12", 2025));

        assertEquals("GD12", info.getIndeks());
        verify(studentRepository).save(student);
    }

    @Test
    void izmenaNaIndeksDrugogStudentaJe400() {
        when(studentRepository.postojiDrugiStudent("GD99", 2025, 11L)).thenReturn(true);

        SystemException ex = assertThrows(SystemException.class,
                () -> studentService.update(11L, studentCmd(5L, "gd 99", 2025)));

        assertEquals(400, ex.getCode());
        assertEquals("Student sa ovim indeksom i godinom upisa već postoji u sistemu.", ex.getMessage());
        verify(studentRepository, never()).save(any());
        assertEquals("GD12", student.getIndeks());
    }

    @Test
    void premestanjeMenjaGrupu() {
        studentService.update(11L, studentCmd(6L, "GD12", 2025));

        ArgumentCaptor<Student> c = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(c.capture());
        assertSame(nova, c.getValue().getGrupa());
    }

    @Test
    void nepostojecaGrupaJe404IStudentSeNeMenja() {
        when(grupaRepository.findById(99L)).thenReturn(Optional.empty());

        SystemException ex = assertThrows(SystemException.class,
                () -> studentService.update(11L, studentCmd(99L, "GD12", 2025)));

        assertEquals(404, ex.getCode());
        verify(studentRepository, never()).save(any());
        assertSame(stara, student.getGrupa());
    }

    @Test
    void nepostojeciStudentJe404() {
        when(studentRepository.findById(77L)).thenReturn(Optional.empty());
        SystemException ex = assertThrows(SystemException.class,
                () -> studentService.update(77L, studentCmd(5L, "GD12", 2025)));
        assertEquals(404, ex.getCode());
    }

    @Test
    void izostavljenaOpcionaPoljaSeBrisu() {
        // PUT je pun zamenski zapis: null email/telefon/opština/datum ne sme da ostavi stare vrednosti
        StudentInfo info = studentService.update(11L, studentCmd(5L, "GD12", 2025));

        assertNull(info.getEmail());
        assertNull(info.getBrojTelefona());
        assertNull(info.getOpstina());
        assertNull(info.getDatumRodjenja());
        assertNull(student.getEmail());
        assertNull(student.getOpstina());
    }

    @Test
    void emailSeNormalizujeKaoPriDodavanju() {
        UpdateStudentCmd cmd = studentCmd(5L, "GD12", 2025);
        cmd.setEmail("  Ana@Example.COM ");
        cmd.setOpstina("Bačka Topola");
        cmd.setDatumRodjenja(LocalDate.of(2004, 1, 2));

        studentService.update(11L, cmd);

        assertEquals("ana@example.com", student.getEmail());
        assertEquals("Bačka Topola", student.getOpstina());
        assertEquals(LocalDate.of(2004, 1, 2), student.getDatumRodjenja());
    }

    @Test
    void praznEmailPostajeNull() {
        UpdateStudentCmd cmd = studentCmd(5L, "GD12", 2025);
        cmd.setEmail("   ");
        studentService.update(11L, cmd);
        assertNull(student.getEmail());
    }

    // ---- grupa

    @Test
    void izmenaGrupeMenjaNazivIGodinu() {
        when(studentRepository.countByGrupaId(5L)).thenReturn(3L);

        GrupaInfo info = grupaService.update(5L, new UpdateGrupaCmd("  GD-2025b ", 2024));

        assertEquals("GD-2025b", info.getNaziv());
        assertEquals(2024, info.getGodinaUpisa());
        assertEquals(3L, info.getBrojStudenata());
        assertEquals("GD-2025b", stara.getNaziv());
    }

    @Test
    void izmenaGrupeNaNazivDrugeGrupeJe400() {
        when(grupaRepository.existsByNazivIgnoreCaseAndIdNot("GD-2026", 5L)).thenReturn(true);

        SystemException ex = assertThrows(SystemException.class,
                () -> grupaService.update(5L, new UpdateGrupaCmd("GD-2026", 2025)));

        assertEquals(400, ex.getCode());
        assertEquals("Grupa sa nazivom GD-2026 već postoji.", ex.getMessage());
        verify(grupaRepository, never()).save(any());
    }

    @Test
    void izmenaGrupeNaSopstveniNazivProlazi() {
        // existsByNazivIgnoreCase bi našao samu grupu; izmena isključuje sopstveni id
        when(grupaRepository.existsByNazivIgnoreCase("GD-2025")).thenReturn(true);
        assertDoesNotThrow(() -> grupaService.update(5L, new UpdateGrupaCmd("GD-2025", 2026)));
        assertEquals(2026, stara.getGodinaUpisa());
    }

    @Test
    void nepostojecaGrupaZaIzmenuJe404() {
        when(grupaRepository.findById(99L)).thenReturn(Optional.empty());
        SystemException ex = assertThrows(SystemException.class,
                () -> grupaService.update(99L, new UpdateGrupaCmd("X", 2025)));
        assertEquals(404, ex.getCode());
    }

    // ---- tip testa

    @Test
    void izmenaTipaTestaMenjaNazivIAktivnost() {
        TipTesta tip = new TipTesta();
        tip.setId(4L);
        tip.setNaziv("Kolokvijum");
        tip.setAktivan(true);
        when(tipTestaRepository.findById(4L)).thenReturn(Optional.of(tip));

        TipTestaInfo info = testService.updateTipTesta(4L, new UpdateTipTestaCmd(" Popravni ", false));

        assertEquals(4L, info.getId());
        assertEquals("Popravni", info.getNaziv());
        assertEquals(Boolean.FALSE, info.getAktivan());
        assertFalse(tip.getAktivan());
    }

    @Test
    void nepostojeciTipTestaJe404() {
        when(tipTestaRepository.findById(88L)).thenReturn(Optional.empty());
        SystemException ex = assertThrows(SystemException.class,
                () -> testService.updateTipTesta(88L, new UpdateTipTestaCmd("X", true)));
        assertEquals(404, ex.getCode());
    }
}
