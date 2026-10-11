package tri.novica.gfssystem.dto.uzivo.platno;


public record SlikaSloj(String id, String naziv, Double x, Double y, Double sirina, Double visina, Double rotacija, Double providnost,
        Boolean zakljucan, Senka senka,
        String medijId, Isecak isecak) implements Sloj {
}
