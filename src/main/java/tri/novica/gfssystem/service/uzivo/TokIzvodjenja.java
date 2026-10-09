package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

import java.util.List;

/** Položaj izvođenja u listi slajdova (zajedničko za komande i stanje). Čiste funkcije. */
public final class TokIzvodjenja {

    private TokIzvodjenja() {}

    /** Pozicija slajda po id-ju ili -1. */
    public static int pozicija(List<Slajd> slajdovi, Long slajdId) {
        if (slajdId == null) return -1;
        for (int i = 0; i < slajdovi.size(); i++) {
            if (slajdId.equals(slajdovi.get(i).getId())) return i;
        }
        return -1;
    }

    /**
     * -1 za prijavu, n za kraj, inače pozicija trenutnog slajda. Slajd kog više nema (obrisan, pre nego što ga
     * {@code slajdObrisan} prebaci) računa se kao kraj.
     */
    public static int indeks(Izvodjenje iz, List<Slajd> slajdovi) {
        return switch (iz.getPrikaz()) {
            case PRIJAVA -> -1;
            case KRAJ -> slajdovi.size();
            case SLAJD -> {
                int i = pozicija(slajdovi, iz.getTrenutniSlajdId());
                yield i < 0 ? slajdovi.size() : i;
            }
        };
    }

    /** Trenutni slajd ili {@code null} (prijava, kraj, obrisan slajd). */
    public static Slajd trenutni(Izvodjenje iz, List<Slajd> slajdovi) {
        int i = indeks(iz, slajdovi);
        return i >= 0 && i < slajdovi.size() ? slajdovi.get(i) : null;
    }

    /** Broj stavki koje se otkrivaju jedna po jedna (INFO sa postepenim otkrivanjem), inače 0. */
    public static int brojStavki(Slajd s) {
        return s != null && s.getTip() == TipSlajda.INFO && s.isPostepeno() ? Markdown.brojStavki(s.getSadrzaj()) : 0;
    }
}
