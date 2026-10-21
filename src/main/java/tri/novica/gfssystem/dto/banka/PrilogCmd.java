package tri.novica.gfssystem.dto.banka;

/**
 * Prilog varijante u komandi: ime kojim ga LaTeX zove ({@code \includegraphics{ime}}, {@code ^[a-z0-9_-]{1,60}$}, ne
 * {@code dokument}) i id medija iz {@code POST /banka/prilozi} (PNG, JPEG ili PDF).
 */
public record PrilogCmd(String ime, String medijId) {
}
