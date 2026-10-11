package tri.novica.gfssystem.dto.uzivo.platno;

public record Animacija(String id, String slojId, Integer pasus, VrstaAnimacije vrsta, Efekat efekat, Smer smer,
                        Okidac okidac, Integer trajanjeMs, Integer kasnjenjeMs) {
}
