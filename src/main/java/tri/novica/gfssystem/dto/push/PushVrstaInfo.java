package tri.novica.gfssystem.dto.push;

/** Prekidač jedne vrste nastavničkog obaveštenja ({@code VrstaObavestenja}) za push, sa nazivom za prikaz. */
public record PushVrstaInfo(String vrsta, String naziv, boolean ukljuceno) {
}
