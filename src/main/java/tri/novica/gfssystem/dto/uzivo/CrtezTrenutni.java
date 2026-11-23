package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.PozadinaCrteza;

/** Trenutna strana crteža u snimku nastavnika ({@link NastavnickoStanje#crtez()}); {@code verzija} 0 = još nema crteža. */
public record CrtezTrenutni(Long slajdId, PozadinaCrteza pozadina, int verzija) {
}
