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
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.StudentRepository;

import java.util.List;

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
}
