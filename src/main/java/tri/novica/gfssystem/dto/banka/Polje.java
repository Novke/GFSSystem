package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;

import java.util.List;

/**
 * Jedno polje šeme odgovora grupe zadataka ({@code GrupaZadataka.polja} je JSON lista ovakvih). Pravila i podrazumevane
 * vrednosti: {@code BankaPP.normalizujPolja} i {@code proveriPolja}. {@code odstupanje} i {@code odstupanjeTip} važe za
 * BROJ, {@code zanemariVelicinu}, {@code zanemariRazmake} i {@code zanemariVodeceNule} za TEKST, {@code opcije} za
 * JEDAN_TACAN i VISE_TACNIH.
 * <p>
 * {@code zanemariVodeceNule} (poslednje, dodato kasnije; podrazumevano {@code false}): {@code 01111111} i {@code 1111111}
 * su isti odgovor. Uključuje se samo gde nula ispred ne menja rezultat (binarni ili heksadekadni zbir, konverzija); u
 * zapisu fiksne širine (komplement, 8 bita) ostaje isključeno, jer je tamo {@code 00000101} drugo od {@code 101}. Stara
 * šema i snimak runde bez polja čitaju se kao {@code false}.
 */
public record Polje(String kljuc, String oznaka, TipPolja tip, Double odstupanje, OdstupanjeTip odstupanjeTip,
                    String jedinica, Boolean zanemariVelicinu, Boolean zanemariRazmake, List<PoljeOpcija> opcije,
                    Boolean zanemariVodeceNule) {

    /** Polje bez {@code zanemariVodeceNule} ({@code null} = isključeno), kao pre te opcije. */
    public Polje(String kljuc, String oznaka, TipPolja tip, Double odstupanje, OdstupanjeTip odstupanjeTip,
                 String jedinica, Boolean zanemariVelicinu, Boolean zanemariRazmake, List<PoljeOpcija> opcije) {
        this(kljuc, oznaka, tip, odstupanje, odstupanjeTip, jedinica, zanemariVelicinu, zanemariRazmake, opcije, null);
    }
}
