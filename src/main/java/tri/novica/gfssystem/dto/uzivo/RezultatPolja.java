package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.TipPolja;

import java.util.List;

/**
 * Rezultat jednog polja varijante: {@code vrednosti} (BROJ, TEKST: grupe istih odgovora, najviše 10, {@code null} dok
 * rezultati nisu prikazani) ili {@code opcije} (JEDAN_TACAN, VISE_TACNIH: svaka opcija šeme sa brojem glasova);
 * {@code tacno} je tačan odgovor varijante, {@code null} dok tačan odgovor nije prikazan.
 */
public record RezultatPolja(String kljuc, String oznaka, TipPolja tip, String jedinica, List<RezultatTekst> vrednosti,
                            List<RezultatOpcija> opcije, OdgovorPolja tacno) {
}
