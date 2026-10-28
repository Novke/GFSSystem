package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.List;

/**
 * Zbirni rezultat jedne runde; popunjeno je samo polje koje odgovara tipu pitanja, ostala su {@code null}.
 * {@code tacnih} je broj tačnih među {@code ukupno} odgovora (opcije: ceo izbor tačan, kod "više tačnih" tačno skup tačnih;
 * broj u odstupanju; kratak tekst među prihvatljivima), {@code null} kad pitanje nema tačan odgovor (anketa, skala,
 * procena, otvoren kratak tekst) ili tačnost još nije vidljiva (javni rezultat pre {@code TACAN} na zatvorenom pitanju).
 */
public record Rezultat(TipPitanja tip, int ukupno, List<RezultatOpcija> opcije, RezultatBrojevi brojevi,
                       List<RezultatTekst> tekstovi, RezultatSkala skala, Integer tacnih) {
}
