package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.Faza;

/**
 * Zadatak u javnom stanju (telefoni, javni topik), bez ijednog sadržaja varijante: faza, runda, tajmer, koliko
 * različitih varijanti ide ({@code CEKA}: koliko će se izvući; posle otvaranja: koliko je izvučeno) i da li je rešenje
 * uključeno na zatvorenoj rundi.
 */
public record JavniZadatak(Faza faza, Long rundaId, Long rokMs, Long preostaloMs, int brojVarijanti, String grupaNaziv,
                           boolean resenjePrikazano) {
}
