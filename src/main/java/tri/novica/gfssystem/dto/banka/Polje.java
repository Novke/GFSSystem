package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;

import java.util.List;

/**
 * Jedno polje šeme odgovora grupe zadataka ({@code GrupaZadataka.polja} je JSON lista ovakvih). Pravila i podrazumevane
 * vrednosti: {@code BankaPP.normalizujPolja} i {@code proveriPolja}. {@code odstupanje} i {@code odstupanjeTip} važe za
 * BROJ, {@code zanemariVelicinu} i {@code zanemariRazmake} za TEKST, {@code opcije} za JEDAN_TACAN i VISE_TACNIH.
 */
public record Polje(String kljuc, String oznaka, TipPolja tip, Double odstupanje, OdstupanjeTip odstupanjeTip,
                    String jedinica, Boolean zanemariVelicinu, Boolean zanemariRazmake, List<PoljeOpcija> opcije) {
}
