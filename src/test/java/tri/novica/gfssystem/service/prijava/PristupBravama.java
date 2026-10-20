package tri.novica.gfssystem.service.prijava;

/** Pristup imenima MySQL brava iz testova u drugim paketima (sami servisi drže pomoćne metode paketno). */
public final class PristupBravama {

    private PristupBravama() {}

    /** Ime brave "Ne stiže mi kod" za dati indeks (kao u {@code ObavestenjeServis}). */
    public static String bravaPomoci(KodServis kodovi, String indeks) {
        return "gfs-pomoc:" + kodovi.indeks(indeks).hmac().substring(0, 32);
    }

    /** Ime brave slanja koda za dati indeks (kao u {@code KodServis}). */
    public static String bravaKoda(KodServis kodovi, String indeks) {
        return "gfs-kod:" + kodovi.indeks(indeks).hmac().substring(0, 32);
    }
}
