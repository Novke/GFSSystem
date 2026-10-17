package tri.novica.gfssystem.dto.banka;

/**
 * Ishod jednog poziva rendera cele banke predmeta: {@code renderovano} varijanti je prevedeno u ovom pozivu,
 * {@code preostalo} varijanti sa zastarelim renderom čeka sledeći poziv (front zove ponovo dok nije 0).
 */
public record RenderujSveInfo(int renderovano, int preostalo) {
}
