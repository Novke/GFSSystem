package tri.novica.gfssystem.dto.push;

/** Izmena prekidača jedne vrste ({@code PUT /push/podesavanja}); nepoznata vrsta ili bez {@code ukljuceno} je 400. */
public record PushVrstaCmd(String vrsta, Boolean ukljuceno) {
}
