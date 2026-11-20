package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.Utisanje;

/**
 * Lično stanje jednog učesnika ({@code /user/queue/licno}): poeni iz poslednje runde svakog slajda, mesto samo u
 * takmičenju, odgovor na trenutnu rundu ({@code null} kad runde nema); {@code zadatak}: sopstvena varijanta trenutne
 * runde Zadatak slajda ({@link LicniZadatak}), {@code null} u CEKA, bez dodele i van Zadatak slajda. {@code tacnih} od
 * {@code dugovanih}: sopstvena tačnost po pravilu ranga tačnosti (poslednja runda svakog slajda, samo pitanja sa tačnim
 * odgovorom i zadaci sa poljima, propušteno je netačno; {@code tacnih} je zbir udela zaokružen na 0,01), a trenutna
 * runda ulazi tek kad je tačan odgovor prikazan, kao poeni. {@code utisan}: nastavnik je učesniku isključio
 * odgovaranje do kraja izvođenja ({@code PITALICE} ili {@code SVE}, inače {@code null}); telefon tada umesto polja za
 * odgovor pokazuje poruku.
 */
public record LicnoStanje(long verzija, Long ucesnikId, String ime, int poeni, Integer mesto, int brojUcesnika,
                          boolean izbacen, LicniOdgovor odgovor, LicniZadatak zadatak, double tacnih, int dugovanih,
                          Utisanje utisan) {

    public LicnoStanje(long verzija, Long ucesnikId, String ime, int poeni, Integer mesto, int brojUcesnika,
                       boolean izbacen, LicniOdgovor odgovor, LicniZadatak zadatak, double tacnih, int dugovanih) {
        this(verzija, ucesnikId, ime, poeni, mesto, brojUcesnika, izbacen, odgovor, zadatak, tacnih, dugovanih, null);
    }

    public LicnoStanje(long verzija, Long ucesnikId, String ime, int poeni, Integer mesto, int brojUcesnika,
                       boolean izbacen, LicniOdgovor odgovor) {
        this(verzija, ucesnikId, ime, poeni, mesto, brojUcesnika, izbacen, odgovor, null, 0, 0);
    }
}
