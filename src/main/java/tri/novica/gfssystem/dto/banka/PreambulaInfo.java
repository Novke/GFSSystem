package tri.novica.gfssystem.dto.banka;

/**
 * LaTeX preambula predmeta ({@code GET|PUT /predmeti/{id}/latex-preambula}): {@code preambula} je {@code null} i
 * {@code podrazumevana} {@code true} dok predmet koristi podrazumevanu; {@code podrazumevanaPreambula} je njen tekst
 * (polazna tačka za izmenu u editoru).
 */
public record PreambulaInfo(String preambula, boolean podrazumevana, String podrazumevanaPreambula) {
}
