package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.uzivo.Faza;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class IzvodjenjeOdrzavanjeTest {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDateTime SADA = LocalDateTime.of(2026, 10, 7, 12, 0);

    final IzvodjenjeRepository izvodjenjeRepository = mock(IzvodjenjeRepository.class);
    final PitanjeRundaRepository rundaRepository = mock(PitanjeRundaRepository.class);
    final IzvodjenjeService servis = mock(IzvodjenjeService.class);
    final RokPlaner planer = mock(RokPlaner.class);
    final IzvodjenjeOdrzavanje odrzavanje = new IzvodjenjeOdrzavanje(izvodjenjeRepository, rundaRepository, servis,
            planer, new MutableClock(SADA.atZone(ZONA).toInstant(), ZONA));

    Izvodjenje otvoreno(long id, long rundaId, LocalDateTime rok) {
        Izvodjenje iz = new Izvodjenje();
        iz.setId(id);
        iz.setStatus(StatusIzvodjenja.AKTIVNO);
        iz.setFaza(Faza.OTVORENO);
        iz.setTrenutnaRundaId(rundaId);
        PitanjeRunda r = new PitanjeRunda();
        r.setId(rundaId);
        r.setRok(rok);
        when(rundaRepository.findById(rundaId)).thenReturn(Optional.of(r));
        return iz;
    }

    @Test
    void posleStartaZakazujeIliZatvara() {
        Izvodjenje buduci = otvoreno(1, 11, SADA.plusSeconds(10));
        Izvodjenje prosao = otvoreno(2, 12, SADA.minusSeconds(2));
        Izvodjenje bezRoka = otvoreno(3, 13, null);
        Izvodjenje ceka = otvoreno(4, 14, SADA.minusSeconds(30));
        ceka.setFaza(Faza.ZATVORENO);
        when(izvodjenjeRepository.findAllByStatus(StatusIzvodjenja.AKTIVNO))
                .thenReturn(List.of(buduci, prosao, bezRoka, ceka));
        doThrow(new RuntimeException("x")).when(planer).zakazi(1L, 11L, SADA.plusSeconds(10));

        odrzavanje.posleStarta();

        verify(planer).zakazi(1L, 11L, SADA.plusSeconds(10));
        verify(servis).zatvoriPoRoku(2L, 12L);   // greška kod prvog ne zaustavlja ostale
        verifyNoMoreInteractions(servis);
    }

    @Test
    void zavrsavaStarijaOd12h() {
        when(izvodjenjeRepository.findIdsStarijaOd(StatusIzvodjenja.AKTIVNO, SADA.minusHours(12)))
                .thenReturn(List.of(7L, 8L));
        doThrow(new RuntimeException("x")).when(servis).zavrsiAutomatski(7L);
        odrzavanje.zavrsiStara();
        verify(servis).zavrsiAutomatski(7L);
        verify(servis).zavrsiAutomatski(8L);
    }
}
