package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.PozadinaCrteza;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ne-prazna strana crteža izvođenja za pregled: {@code slajdBroj} je redni broj slajda u prezentaciji (od 1), a
 * {@code slajdId} i {@code slajdBroj} su {@code null} kad je slajd posle obrisan.
 */
public record CrtezIzvodjenjaInfo(Long slajdId, Integer slajdBroj, PozadinaCrteza pozadina, List<ElementCrteza> elementi,
                                  int verzija, LocalDateTime izmenjeno) {
}
