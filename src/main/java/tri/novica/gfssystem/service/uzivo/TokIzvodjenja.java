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

    /**
     * Broj koraka koji se otkrivaju jedan po jedan: INFO sa postepenim otkrivanjem (stavke liste), PLATNO (animacije sa
     * okidačem KLIK), inače 0.
     */
    public static int brojKoraka(Slajd s) {
        if (s == null) return 0;
        return switch (s.getTip()) {
            case INFO -> s.isPostepeno() ? Markdown.brojStavki(s.getSadrzaj()) : 0;
            case PLATNO -> PlatnoJson.brojKoraka(s.getPlatno());
            default -> 0;
        };
    }

    /** Slajd koji otvara runde: PITANJE sa pitanjem ili ZADATAK (i bez grupe; otvaranje tada javlja 409). */
    public static boolean saRundama(Slajd s) {
        if (s == null) return false;
        return (s.getTip() == TipSlajda.PITANJE && s.getPitanje() != null) || s.getTip() == TipSlajda.ZADATAK;
    }

    /**
     * Broj varijanti koje se izvlače pri otvaranju ZADATAK slajda, {@code null} = sve aktivne: izmena iz konzole
     * ({@code Izvodjenje.zadatakBrojVarijanti}: {@code null} = kao na slajdu, 0 = sve), inače broj sa slajda.
     */
    public static Integer brojVarijanti(Izvodjenje iz, Slajd s) {
        Integer izmena = iz.getZadatakBrojVarijanti();
        if (izmena == null) return s.getZadatakBrojVarijanti();
        return izmena == 0 ? null : izmena;
    }

    /**
     * Da li runda sa ovim snimkom pripada slajdu ovakvog tipa ({@link PitanjeSnimak} za PITANJE, {@link ZadatakSnimak}
     * za ZADATAK). Slajd može promeniti tip usred runa, pa runda druge vrste ne sme postati njegova trenutna.
     */
    public static boolean odgovaraSlajdu(SnimakRunde sn, Slajd s) {
        if (sn == null || s == null) return false;
        return switch (s.getTip()) {
            case PITANJE -> sn instanceof PitanjeSnimak;
            case ZADATAK -> sn instanceof ZadatakSnimak;
            default -> false;
        };
    }
}
