package tri.novica.gfssystem.entity.prijava;

/** Vrsta obaveštenja za nastavnika (kolona {@code obavestenja.vrsta}, varchar(40)). */
public enum VrstaObavestenja {
    /** Student je javio "Ne stiže mi kod". */
    NE_STIZE_KOD("Studentu ne stiže kod"),
    /** Student je pogodio limit kodova na sat (najviše jedno dnevno po studentu). */
    PREVISE_KODOVA("Previše kodova"),
    /**
     * Za jedan termin rasporeda odsustvo je najavilo više studenata od praga perioda (jednom po terminu, ključ
     * {@code MNOGO_NAJAVA:<stavkaId>:<datum>}; bez studenta).
     */
    MNOGO_NAJAVA("Mnogo najava odsustva");

    private final String naziv;

    VrstaObavestenja(String naziv) {
        this.naziv = naziv;
    }

    /** Naziv vrste u nastavničkim podešavanjima push-a ({@code GET /push/podesavanja}). */
    public String naziv() {
        return naziv;
    }
}
