package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.config.NastavnikSvojstva;
import tri.novica.gfssystem.repository.prijava.NastavnickaSesijaRepository;
import tri.novica.gfssystem.repository.push.PushPretplataRepository;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** Spec 4.1 (fail-closed): kad prijava nastavnika nije podešena, nijedan token ne važi, ni sesija koja postoji u bazi. */
class NastavnikSesijaServisNepodesenoTest {

    @Test
    void potvrdiBezPodesavanjaNeGledaBazu() {
        NastavnickaSesijaRepository repo = mock(NastavnickaSesijaRepository.class);
        NastavnikSesijaServis servis = new NastavnikSesijaServis(new NastavnikSvojstva("", "", ""), repo,
                mock(PushPretplataRepository.class), mock(PokusajiServis.class), mock(TokenGenerator.class),
                Clock.systemDefaultZone());
        assertEquals(Optional.empty(), servis.potvrdi("A".repeat(32)));
        verifyNoInteractions(repo);
    }
}
