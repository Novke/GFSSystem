package tri.novica.gfssystem.dto.banka;

import java.time.LocalDateTime;

/**
 * Klodova serija sa brojačima (spec MCP 6.1: {@code GET /banka/serije}, MCP {@code kontekst}): {@code nacrta} = varijante
 * serije u nacrtu koje je napravio Klod (CLAUDE, NACRT), {@code odobrenih} = varijante serije koje su bar jednom odobrene
 * ({@code ikadOdobrena}), {@code grupa}, {@code prezentacija} i {@code slajdova} serije. {@code poslednja} = najnovije
 * {@code kreirano} grupa, varijanti i prezentacija serije, a za slajdove (nemaju svoje vreme) {@code izmenjeno} njihove
 * prezentacije.
 */
public record SerijaInfo(String serija, long nacrta, long odobrenih, long grupa, long prezentacija, long slajdova,
                         LocalDateTime poslednja) {
}
