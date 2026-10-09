package tri.novica.gfssystem.dto.student;

/**
 * Filteri liste studenata ({@code GET /studenti/pretraga}); svako polje je opciono, null = bez filtera.
 * {@code starijiOdGrupe}: studenti iz grupa sa manjom godinom upisa od grupe sa tim id-jem (nepostojeća grupa je 404).
 * {@code q} traži u imenu, prezimenu, punom imenu i indeksu (bez razmaka), bez obzira na velika i mala slova.
 */
public record StudentFilter(Long grupaId, Long starijiOdGrupe, String q) {
}
