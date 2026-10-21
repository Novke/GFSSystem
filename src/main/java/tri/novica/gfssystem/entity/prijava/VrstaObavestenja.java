package tri.novica.gfssystem.entity.prijava;

/** Vrsta obaveštenja za nastavnika (kolona {@code obavestenja.vrsta}, varchar(40)). */
public enum VrstaObavestenja {
    /** Student je javio "Ne stiže mi kod". */
    NE_STIZE_KOD,
    /** Student je pogodio limit kodova na sat (najviše jedno dnevno po studentu). */
    PREVISE_KODOVA
}
