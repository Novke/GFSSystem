package tri.novica.gfssystem.dto.test.online;

/** Meki znak sa ekrana: {@code NAPUSTIO} ili {@code VRATIO} (uz trajanje). */
public record DogadjajCmd(String vrsta, Long trajanjeMs) {
}
