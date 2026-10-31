package tri.novica.gfssystem.dto.portal;

import java.util.List;

/**
 * ZADATAK slajd u pregledu (spec 6.1): naziv tipa sa slajda ({@code null} kad je tip obrisan), sopstveni zadaci sa časa
 * sa tog slajda i tipovi ispod čvora slajda koje grupa studenta sme da vežba. Tuđe varijante nikad.
 */
public record ZadatakPregleda(String naziv, List<MojZadatak> moji, List<TipZaVezbanje> tipovi) {
}
