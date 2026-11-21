package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.List;

/**
 * Zbirni rezultat jedne runde; popunjeno je samo polje koje odgovara tipu pitanja, ostala su {@code null}.
 * {@code tacnih} je broj tačnih među {@code ukupno} odgovora (opcije: ceo izbor tačan, kod "više tačnih" tačno skup tačnih;
 * broj u odstupanju; kratak tekst među prihvatljivima), {@code null} kad pitanje nema tačan odgovor (anketa, skala,
 * procena, otvoren kratak tekst) ili tačnost još nije vidljiva (javni rezultat pre {@code TACAN} na zatvorenom pitanju).
 * {@code delimicnih} je broj odgovora sa udelom između 0 i 1 (samo "više tačnih"), {@code zbirUdela} zbir udela svih
 * odgovora zaokružen na 0,01; oba su {@code null} kad je {@code tacnih} {@code null}.
 */
public record Rezultat(TipPitanja tip, int ukupno, List<RezultatOpcija> opcije, RezultatBrojevi brojevi,
                       List<RezultatTekst> tekstovi, RezultatSkala skala, Integer tacnih, Integer delimicnih,
                       Double zbirUdela) {
}
