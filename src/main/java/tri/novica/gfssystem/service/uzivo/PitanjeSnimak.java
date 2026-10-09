package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.entity.uzivo.*;

import java.util.Comparator;
import java.util.List;

/**
 * Kopija pitanja u trenutku otvaranja runde (čuva se kao JSON u {@code PitanjeRunda.snimak}), pa ocena i rezultati
 * ostaju tačni i kad se slajd kasnije izmeni ili obriše.
 */
public record PitanjeSnimak(Long pitanjeId, Long slajdId, TipPitanja tip, String tekst, String slikaId,
                            Integer vremeSekunde, List<OpcijaSnimak> opcije, Double brojTacno, Double brojOdstupanje,
                            OdstupanjeTip odstupanjeTip, String jedinica, TekstPrikaz tekstPrikaz,
                            List<String> prihvatljiviOdgovori, String skalaMinOznaka, String skalaMaxOznaka) {

    /** Snimak pitanja sa slajda; opcije po {@code rb}. */
    public static PitanjeSnimak od(Slajd slajd) {
        Pitanje p = slajd.getPitanje();
        if (p == null) throw new IllegalArgumentException("Slajd nema pitanje.");
        List<OpcijaSnimak> opcije = p.getOpcije().stream()
                .sorted(Comparator.comparingInt(PitanjeOpcija::getRb))
                .map(o -> new OpcijaSnimak(o.getId(), o.getRb(), o.getTekst(), o.isTacna()))
                .toList();
        List<String> prihvatljivi = p.getPrihvatljiviOdgovori() == null ? null : List.copyOf(p.getPrihvatljiviOdgovori());
        return new PitanjeSnimak(p.getId(), slajd.getId(), p.getTip(), p.getTekst(),
                p.getSlika() == null ? null : p.getSlika().getId(), p.getVremeSekunde(), opcije, p.getBrojTacno(),
                p.getBrojOdstupanje(), p.getOdstupanjeTip(), p.getJedinica(), p.getTekstPrikaz(), prihvatljivi,
                p.getSkalaMinOznaka(), p.getSkalaMaxOznaka());
    }

    /** Da li odgovor može biti tačan ili netačan (anketa i skala ne mogu; broj bez tačne vrednosti je procena). */
    public boolean imaTacanOdgovor() {
        return switch (tip) {
            case JEDAN_TACAN, VISE_TACNIH, TACNO_NETACNO -> true;
            case BROJ -> brojTacno != null;
            case KRATAK_TEKST -> prihvatljiviOdgovori != null && !prihvatljiviOdgovori.isEmpty();
            case ANKETA, SKALA -> false;
        };
    }
}
