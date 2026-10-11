package tri.novica.gfssystem.dto.uzivo.platno;


public record FormulaSloj(String id, String naziv, Double x, Double y, Double sirina, Double visina, Double rotacija, Double providnost,
        Boolean zakljucan, Senka senka,
        String latex, String boja, Double velicina, Poravnanje poravnanje, String slikaIzvora) implements Sloj {
}
