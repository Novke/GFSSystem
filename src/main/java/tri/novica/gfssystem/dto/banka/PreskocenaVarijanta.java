package tri.novica.gfssystem.dto.banka;

/** Varijanta koju masovna radnja nije promenila, sa razlogom (poruka istog pravila kao za jednu varijantu). */
public record PreskocenaVarijanta(Long id, String naziv, String razlog) {
}
