package tri.novica.gfssystem.dto.portal;

/**
 * "Tvoj odgovor" na PITANJE slajd sa času (poslednja runda završenog izvođenja): tekst i tačno ({@code null} za anketu);
 * {@code udeo} 0..1 (delimično tačno kod "više tačnih", ostali tipovi 1/0), {@code null} kad pitanje nema tačan odgovor.
 */
public record OdgovorPregleda(String tekst, Boolean tacno, Double udeo) {
}
