package tri.novica.gfssystem.dto.uzivo.platno;

import java.util.ArrayList;
import java.util.List;

/** Dokument platna ({@code verzija: 1}); isti oblik u ulazu, izlazu i koloni {@code slajdovi.platno}. */
public record Platno(Integer verzija, Pozadina pozadina, List<Sloj> slojevi, List<Animacija> animacije) {

    /** Kopija u kojoj svaki crtež ima {@code potezi = null} (snimak izvođenja ne nosi poteze). */
    public Platno bezPoteza() {
        List<Sloj> novi = new ArrayList<>();
        if (slojevi != null) {
            for (Sloj s : slojevi) {
                if (s instanceof CrtezSloj c) {
                    novi.add(new CrtezSloj(c.id(), c.naziv(), c.x(), c.y(), c.sirina(), c.visina(), c.rotacija(),
                            c.providnost(), c.zakljucan(), c.senka(), c.medijId(), null));
                } else {
                    novi.add(s);
                }
            }
        }
        return new Platno(verzija, pozadina, slojevi == null ? null : novi, animacije);
    }
}
