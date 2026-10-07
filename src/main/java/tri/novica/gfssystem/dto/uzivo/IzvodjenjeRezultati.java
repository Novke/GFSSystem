package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Pregled izvođenja: pitanja po redu otvaranja i cela rang-lista. */
public record IzvodjenjeRezultati(IzvodjenjeInfo izvodjenje, List<RezultatPitanja> pitanja, List<RangStavka> rangLista) {
}
