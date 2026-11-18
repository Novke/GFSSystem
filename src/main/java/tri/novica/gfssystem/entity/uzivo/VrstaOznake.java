package tri.novica.gfssystem.entity.uzivo;

/** Vrsta oznake ponašanja na kartonu studenta ({@link OznakaPonasanja}). */
public enum VrstaOznake {
    SAKRIVEN_ODGOVOR, UTISAN_PITALICE, UTISAN_SVE, IZBACEN, IZBACEN_PREDAVANJE;

    public static VrstaOznake zaUtisanje(Utisanje u) {
        return u == Utisanje.SVE ? UTISAN_SVE : UTISAN_PITALICE;
    }
}
