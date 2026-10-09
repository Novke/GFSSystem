package tri.novica.gfssystem.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import tri.novica.gfssystem.dto.test.CreateTestCmd;
import tri.novica.gfssystem.dto.test.PragProlazaCmd;
import tri.novica.gfssystem.dto.test.TestDetails;
import tri.novica.gfssystem.dto.test.TestInfo;
import tri.novica.gfssystem.dto.test.UpdateTestCmd;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.validation.TestPP;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Opcioni prag prolaza po testu: bean validacija komandi, provera praga prema maksimumu (TestPP), upis pri kreiranju
 * i PUT-u zaglavlja (prag se može i ukloniti) i prolaznost u detaljima testa po pragu, ne po polju polozio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestPragProlazaTest {

    static final String PORUKA = "Prag prolaza mora biti između 0 i maksimalnog broja poena.";

    @Mock TestRepository testRepository;
    @Mock TipTestaRepository tipTestaRepository;
    @Mock PredmetRepository predmetRepository;
    @Mock GrupaRepository grupaRepository;
    @Mock StudentRepository studentRepository;
    @Mock PolaganjeRepository polaganjeRepository;

    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    ModelMapper mapper;
    TestService service;
    tri.novica.gfssystem.entity.Test test;
    TipTesta tip;

    @BeforeEach
    void setUp() {
        mapper = new ModelMapper();   // isto podešavanje kao bean u GfsSystemApplication
        mapper.getConfiguration().setPropertyCondition(c -> c.getSource() != null).setSkipNullEnabled(true)
                .setMatchingStrategy(MatchingStrategies.STRICT);
        service = new TestService(testRepository, tipTestaRepository, predmetRepository, grupaRepository,
                studentRepository, polaganjeRepository, mapper, new TestPP());

        Predmet predmet = new Predmet();
        predmet.setId(1L);
        Grupa grupa = new Grupa();
        grupa.setId(2L);
        grupa.setNaziv("GD-2025");
        grupa.setGodinaUpisa(2025);
        tip = new TipTesta("Kolokvijum", predmet);
        tip.setId(4L);
        test = new tri.novica.gfssystem.entity.Test();
        test.setId(10L);
        test.setPredmet(predmet);
        test.setGrupa(grupa);
        test.setTipTesta(tip);
        test.setMaxPoena(40);
        test.setPregledan(false);
        test.setGrupe(java.util.Set.of(TestGrupa.A, TestGrupa.B));
        when(predmetRepository.findById(1L)).thenReturn(Optional.of(predmet));
        when(grupaRepository.findById(2L)).thenReturn(Optional.of(grupa));
        when(tipTestaRepository.findById(4L)).thenReturn(Optional.of(tip));
        when(testRepository.findByIdFetchPolaganja(10L)).thenReturn(Optional.of(test));
        when(testRepository.save(any(tri.novica.gfssystem.entity.Test.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CreateTestCmd create(Integer max, Integer prag) {
        return new CreateTestCmd(4L, null, 1L, 2L, LocalDate.of(2025, 11, 1), 2, max, prag);
    }

    // ------------------------------------------------------------------ validacija

    @Test
    void negativanPragOdbijaBeanValidacija() {
        var v = validator.validate(create(40, -1));
        assertEquals(1, v.size());
        assertEquals(PORUKA, v.iterator().next().getMessage());
        assertEquals(1, validator.validate(new PragProlazaCmd(-5)).size());
        assertTrue(validator.validate(create(40, null)).isEmpty());
        assertTrue(validator.validate(create(40, 0)).isEmpty());
        assertTrue(validator.validate(new PragProlazaCmd(null)).isEmpty());
    }

    @Test
    void pragPrekoMaksimumaOdbijaSeKaoBadRequest() {
        var ex = assertThrows(SystemException.class, () -> service.createTest(create(40, 41)));
        assertEquals(PORUKA, ex.getMessage());
        assertEquals(400, ex.getCode());
        ex = assertThrows(SystemException.class, () -> service.postaviPragProlaza(10L, new PragProlazaCmd(41)));
        assertEquals(PORUKA, ex.getMessage());
        assertEquals(400, ex.getCode());
    }

    @Test
    void sniziMaksimumIspodPragaOdbijaSePriPutu() {
        test.setPragProlaza(30);
        assertThrows(SystemException.class, () -> service.updateTest(10L, new UpdateTestCmd(LocalDate.now(), 20, 4L)));
    }

    // ------------------------------------------------------------------ upis

    @Test
    void kreiranjeCuvaPragIPragNaMaksimumuJeDozvoljen() {
        TestInfo i = service.createTest(create(40, 40));
        assertEquals(40, i.getPragProlaza());
        assertNull(service.createTest(create(40, null)).getPragProlaza());
    }

    @Test
    void putNeDiraPrag() {
        test.setPragProlaza(20);
        TestDetails d = service.updateTest(10L, new UpdateTestCmd(LocalDate.of(2025, 11, 2), 40, 4L));
        assertEquals(20, d.getPragProlaza());
        assertEquals(20, test.getPragProlaza());
    }

    @Test
    void patchPostavljaIUklanjaPragNaPregledanomTestu() {
        test.setPregledan(true);   // PUT bi ovde odbio, PATCH prag sme
        assertEquals(20, service.postaviPragProlaza(10L, new PragProlazaCmd(20)).getPragProlaza());
        assertEquals(40, service.postaviPragProlaza(10L, new PragProlazaCmd(40)).getPragProlaza());   // prag == max
        assertEquals(0, service.postaviPragProlaza(10L, new PragProlazaCmd(0)).getPragProlaza());
        assertNull(service.postaviPragProlaza(10L, new PragProlazaCmd(null)).getPragProlaza());
        assertNull(test.getPragProlaza());
    }

    @Test
    void patchNepostojecegTestaJe404() {
        var ex = assertThrows(SystemException.class, () -> service.postaviPragProlaza(99L, new PragProlazaCmd(10)));
        assertEquals(404, ex.getCode());
    }

    // ------------------------------------------------------------------ prolaznost u detaljima

    private void polaganje(Double poeni, Boolean polozio, boolean prepisivao, TestGrupa grupa) {
        Polaganje p = Polaganje.defaultPolaganje(test, null);
        p.setId((long) test.getPolaganja().size() + 1);
        p.setOstvareniPoeni(poeni);
        p.setPolozio(polozio);
        p.setPrepisivao(prepisivao);
        p.setGrupa(grupa);
        test.getPolaganja().add(p);
    }

    private void polaganja() {
        polaganje(20.0, null, false, TestGrupa.A);    // tačno na pragu: položio
        polaganje(19.5, true, false, TestGrupa.A);    // ispod praga: pao iako je polozio = true
        polaganje(40.0, true, true, TestGrupa.B);     // prepisivao: pao
        polaganje(null, true, false, TestGrupa.B);    // bez poena: van imenioca
        polaganje(30.0, false, false, TestGrupa.B);   // iznad praga: položio iako je polozio = false
    }

    @Test
    void prolaznostPoPragu() {
        test.setPragProlaza(20);
        polaganja();
        var s = service.findById(10L).getStatistika();
        assertEquals(5, s.getUkupnoPolaganja());
        assertEquals(2, s.getBrojPolozenih());
        assertEquals(2, s.getBrojPalih());
        assertEquals(50.0, s.getProcenatProlaznosti(), 1e-9);
        assertEquals(50.0, s.getStatistikaPoGrupi().get(0).getProcenatProlaznosti(), 1e-9);   // A: 1 od 2
        assertEquals(50.0, s.getStatistikaPoGrupi().get(1).getProcenatProlaznosti(), 1e-9);   // B: 1 od 2 (bez poena van)
    }

    @Test
    void bezPragaProlaznostJeNull() {
        polaganja();
        var s = service.findById(10L).getStatistika();
        assertNotNull(s.getProsecniPoeni());
        assertNull(s.getBrojPolozenih());
        assertNull(s.getBrojPalih());
        assertNull(s.getProcenatProlaznosti());
        s.getStatistikaPoGrupi().forEach(g -> assertNull(g.getProcenatProlaznosti()));
    }
}
