package tri.novica.gfssystem.dto.uzivo;

/**
 * Lično stanje jednog učesnika ({@code /user/queue/licno}): poeni iz poslednje runde svakog slajda, mesto samo u
 * takmičenju, odgovor na trenutnu rundu ({@code null} kad runde nema).
 */
public record LicnoStanje(long verzija, Long ucesnikId, String ime, int poeni, Integer mesto, int brojUcesnika,
                          boolean izbacen, LicniOdgovor odgovor) {
}
