package tri.novica.gfssystem.dto.banka;

/**
 * Ishod jednog poziva rendera cele banke predmeta: {@code renderovano} varijanti je u ovom pozivu dobilo uspeo render
 * ({@code OK}; pala kompilacija ili preskočena loša varijanta se upisuju kao {@code GRESKA} i ne broje se),
 * {@code preostalo} varijanti sa zastarelim renderom čeka sledeći poziv (front zove ponovo dok nije 0).
 */
public record RenderujSveInfo(int renderovano, int preostalo) {
}
