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
 * <p>
 * {@code poeni} ({@code > 0}, najviše 100, {@code null} = 1) i {@code konacno} ({@code null} = {@code false}), dodati za
 * online testove (spec 6.2): koliko polje nosi u odnosu na ostala polja zadatka i da li je konačan rezultat. Čita ih samo
 * bodovanje testa ({@code BodovanjeZadatka}); Uživo i vežbanje ih ignorišu.
 */
public record Polje(String kljuc, String oznaka, TipPolja tip, Double odstupanje, OdstupanjeTip odstupanjeTip,
                    String jedinica, Boolean zanemariVelicinu, Boolean zanemariRazmake, List<PoljeOpcija> opcije,
                    Boolean zanemariVodeceNule, Double poeni, Boolean konacno) {

    /**
     * Polje bez {@code poeni} i {@code konacno} (spec online testova 6.2; {@code null} = 1 poen, nije konačno), kao pre
     * online testova. Čita ih samo bodovanje testa; Uživo i vežbanje ih ignorišu.
     */
    public Polje(String kljuc, String oznaka, TipPolja tip, Double odstupanje, OdstupanjeTip odstupanjeTip,
                 String jedinica, Boolean zanemariVelicinu, Boolean zanemariRazmake, List<PoljeOpcija> opcije,
                 Boolean zanemariVodeceNule) {
        this(kljuc, oznaka, tip, odstupanje, odstupanjeTip, jedinica, zanemariVelicinu, zanemariRazmake, opcije,
                zanemariVodeceNule, null, null);
    }

    /** Polje bez {@code zanemariVodeceNule} ({@code null} = isključeno), kao pre te opcije. */
    public Polje(String kljuc, String oznaka, TipPolja tip, Double odstupanje, OdstupanjeTip odstupanjeTip,
                 String jedinica, Boolean zanemariVelicinu, Boolean zanemariRazmake, List<PoljeOpcija> opcije) {
        this(kljuc, oznaka, tip, odstupanje, odstupanjeTip, jedinica, zanemariVelicinu, zanemariRazmake, opcije, null, null, null);
    }
}
