package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Pregled izvođenja: pitanja po redu otvaranja i cela rang-lista. {@code takmicenje}: da li je izvođenje bilo
 * takmičenje (podešavanje izvođenja, zapamćeno pri pokretanju), pa pregled zna da li da prikaže poene i rang.
 */
public record IzvodjenjeRezultati(IzvodjenjeInfo izvodjenje, boolean takmicenje, List<RezultatPitanja> pitanja,
                                  List<RangStavka> rangLista) {
}
