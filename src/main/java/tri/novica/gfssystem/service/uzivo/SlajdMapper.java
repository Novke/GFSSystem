package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.dto.uzivo.OpcijaDetails;
import tri.novica.gfssystem.dto.uzivo.PitanjeDetails;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.Pitanje;
import tri.novica.gfssystem.entity.uzivo.PitanjeOpcija;
import tri.novica.gfssystem.entity.uzivo.Slajd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Slajd, pitanje i slika u DTO za nastavnika (editor, konzola). Čiste funkcije, bez baze. */
public final class SlajdMapper {

    private SlajdMapper() {}

    public static SlajdDetails uDetails(Slajd s) {
        return new SlajdDetails(s.getId(), s.getRb(), s.getTip(), s.getNaslov(), s.getSadrzaj(), uInfo(s.getSlika()),
                s.getBeleske(), s.isPostepeno(), uDetails(s.getPitanje()));
    }

    /** {@code null} za {@code null}; opcije po {@code rb} (i kad kolekcija nije učitana sa {@code @OrderBy}). */
    public static PitanjeDetails uDetails(Pitanje p) {
        if (p == null) return null;
        List<OpcijaDetails> opcije = p.getOpcije().stream()
                .sorted(Comparator.comparingInt(PitanjeOpcija::getRb))
                .map(o -> new OpcijaDetails(o.getId(), o.getRb(), o.getTekst(), o.isTacna()))
                .toList();
        List<String> prihvatljivi = p.getPrihvatljiviOdgovori() == null ? null : new ArrayList<>(p.getPrihvatljiviOdgovori());
        return new PitanjeDetails(p.getId(), p.getTip(), p.getTekst(), uInfo(p.getSlika()), p.getVremeSekunde(), opcije,
                p.getBrojTacno(), p.getBrojOdstupanje(), p.getOdstupanjeTip(), p.getJedinica(), p.getTekstPrikaz(),
                prihvatljivi, p.getSkalaMinOznaka(), p.getSkalaMaxOznaka());
    }

    public static MedijInfo uInfo(Medij m) {
        return m == null ? null : new MedijInfo(m.getId(), m.getNaziv(), m.getMime(), m.getVelicina());
    }
}
