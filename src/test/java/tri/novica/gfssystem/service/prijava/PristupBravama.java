package tri.novica.gfssystem.service.prijava;

/** Pristup imenima MySQL brava iz testova u drugim paketima (sami servisi drže pomoćne metode paketno). */
public final class PristupBravama {

    private PristupBravama() {}

    /** Ime brave "Ne stiže mi kod" za dati indeks (kao u {@code ObavestenjeServis}). */
    public static String bravaPomoci(KodServis kodovi, String indeks) {
        return kodovi.imeBrave("gfs-pomoc:" + kodovi.indeks(indeks).hmac().substring(0, 32));
    }

    /** Ime brave slanja koda za dati indeks (kao u {@code KodServis}). */
    public static String bravaKoda(KodServis kodovi, String indeks) {
        return kodovi.imeBrave("gfs-kod:" + kodovi.indeks(indeks).hmac().substring(0, 32));
    }

    /** Ime brave ukupnog brojača mejlova nastavniku (sa prefiksom šeme). */
    public static String bravaMejla(KodServis kodovi) {
        return kodovi.imeBrave(ObavestenjeServis.BRAVA_MEJL);
    }

    /** Koliko (s) {@code KodServis} čeka bravu indeksa; testovi spuštaju sa 10 s da ne čekaju zaista. */
    public static void cekanjeBraveIndeksa(KodServis kodovi, int sekundi) {
        kodovi.cekanjeBrave(sekundi);
    }

    /** Koliko (s) {@code ObavestenjeServis} čeka bravu ukupnog brojača mejlova nastavniku. */
    public static void cekanjeBraveMejla(ObavestenjeServis obavestenja, int sekundi) {
        obavestenja.cekanjeBraveMejla(sekundi);
    }

    /** Vraća oba čekanja na podrazumevana (u {@code @AfterEach} testova koji su ih menjali). */
    public static void vratiCekanja(KodServis kodovi, ObavestenjeServis obavestenja) {
        kodovi.cekanjeBrave(KodServis.CEKANJE_BRAVE_S);
        obavestenja.cekanjeBraveMejla(ObavestenjeServis.CEKANJE_BRAVE_MEJL_S);
    }
}
