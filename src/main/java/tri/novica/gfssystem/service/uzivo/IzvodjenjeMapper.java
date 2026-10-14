package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.uzivo.GrupaKratko;
import tri.novica.gfssystem.dto.uzivo.IzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.PredavanjeKratko;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaKratko;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.Prezentacija;

/** Izvođenje u {@link IzvodjenjeInfo}. Čista funkcija; brojeve računa pozivalac. */
public final class IzvodjenjeMapper {

    private IzvodjenjeMapper() {}

    public static IzvodjenjeInfo info(Izvodjenje i, long brojUcesnika, long brojPitanja) {
        Prezentacija p = i.getPrezentacija();
        Grupa g = i.getGrupa();
        Predavanje pr = i.getPredavanje();
        return new IzvodjenjeInfo(i.getId(),
                new PrezentacijaKratko(p.getId(), p.getNaziv(), p.getPredmet().getId()),
                i.getKod(), i.getStatus(), i.isCuvanje(),
                g == null ? null : new GrupaKratko(g.getId(), g.getNaziv()),
                pr == null ? null : new PredavanjeKratko(pr.getId(), pr.getRb(), pr.getDatum(), pr.getTema()),
                i.getPocetak(), i.getKraj(), brojUcesnika, brojPitanja);
    }
}
