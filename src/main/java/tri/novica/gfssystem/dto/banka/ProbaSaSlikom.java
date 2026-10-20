package tri.novica.gfssystem.dto.banka;

/**
 * Render-proba za Kloda (spec MCP 4.3, {@code proba_rendera}): ishod bez SVG-a ({@code svg} i {@code resenjeSvg} su
 * {@code null}) i PNG zadatka i rešenja iz istog prevođenja ({@code zadatak} je {@code null} kad se tekst nije preveo).
 * Čist podatak koji servis vraća fasadi, pa je u {@code dto}, ne u {@code service}.
 */
public record ProbaSaSlikom(RenderProbaInfo info, SlikeRendera slike) {
}
