package tri.novica.gfssystem.dto.uzivo;

/**
 * Lično stanje jednog učesnika ({@code /user/queue/licno}): poeni iz poslednje runde svakog slajda, mesto samo u
 * takmičenju, odgovor na trenutnu rundu ({@code null} kad runde nema); {@code zadatak}: sopstvena varijanta trenutne
 * runde Zadatak slajda ({@link LicniZadatak}), {@code null} u CEKA, bez dodele i van Zadatak slajda.
 */
public record LicnoStanje(long verzija, Long ucesnikId, String ime, int poeni, Integer mesto, int brojUcesnika,
                          boolean izbacen, LicniOdgovor odgovor, LicniZadatak zadatak) {

    public LicnoStanje(long verzija, Long ucesnikId, String ime, int poeni, Integer mesto, int brojUcesnika,
                       boolean izbacen, LicniOdgovor odgovor) {
        this(verzija, ucesnikId, ime, poeni, mesto, brojUcesnika, izbacen, odgovor, null);
    }
}
