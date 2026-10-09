package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KodGeneratorTest {

    final IzvodjenjeRepository repo = mock(IzvodjenjeRepository.class);
    final KodGenerator generator = new KodGenerator(repo);

    @Test
    void sestCifaraISlobodan() {
        when(repo.existsByAktivanKod(anyString())).thenReturn(false);
        Set<String> kodovi = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String kod = generator.novi();
            assertTrue(kod.matches("\\d{6}"), kod);
            kodovi.add(kod);
        }
        assertTrue(kodovi.size() > 150, "kodovi su nasumični");
    }

    @Test
    void preskaceZauzetKod() {
        when(repo.existsByAktivanKod(anyString())).thenReturn(true, true, false);
        String kod = generator.novi();
        assertTrue(kod.matches("\\d{6}"));
        verify(repo, times(3)).existsByAktivanKod(anyString());
    }

    @Test
    void posle20Pokusaja503() {
        when(repo.existsByAktivanKod(anyString())).thenReturn(true);
        SystemException e = assertThrows(SystemException.class, generator::novi);
        assertEquals(503, e.getCode());
        assertEquals("Trenutno nema slobodnog koda, pokušaj ponovo.", e.getMessage());
        verify(repo, times(20)).existsByAktivanKod(anyString());
    }
}
