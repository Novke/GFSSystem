package tri.novica.gfssystem.dto.banka;

/**
 * Ishod jednog poziva rendera cele banke predmeta: {@code renderovano} varijanti je u ovom pozivu dobilo uspeo render
 * ({@code OK}), {@code neuspelo} varijanti je upisano kao {@code GRESKA} (pala kompilacija ili preskočena loša varijanta;
 * status i poslednji uspeo SVG ostaju), {@code preostalo} varijanti sa zastarelim renderom čeka sledeći poziv (front zove
 * ponovo dok nije 0). Varijanta koju je neko u međuvremenu osvežio ili izmenio se ne broji nigde.
 */
public record RenderujSveInfo(int renderovano, int neuspelo, int preostalo) {
}
