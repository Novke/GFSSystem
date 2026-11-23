package tri.novica.gfssystem.entity.push;

/**
 * Vrsta reda u {@code studentska_obavestenja} (spec aplikacije 6.8): isti red je stavka zvona i izvor push poruke.
 * {@link #PODSETNIK} se nikad ne prikazuje u zvonu (red je zapis da je podsetnik poslat); {@link #OTKAZAN_CAS} i
 * {@link #ODRZAVA_SE} se uvek prikazuju; {@link #OCENE} i {@link #POSLE_CASA} po prekidačima zvona studenta. Brza anketa
 * (V27): {@link #ANKETA_CAS} se ne prikazuje (kao podsetnik), {@link #ANKETA} uvek. Starija slika ne čita nove vrste: pre
 * rollback-a preko V27 obrisati te redove (vidi {@code V27__brza_anketa.sql}).
 */
public enum VrstaStudentskogObavestenja {
    /** Podsetnik dan pre časa (push {@code push_podsetnik}; nije u zvonu). */
    PODSETNIK,
    /** Čas je otkazan (push {@code push_otkazan_cas}; zvono uvek). */
    OTKAZAN_CAS,
    /** Otkazan čas se ipak održava (isti prekidač kao otkazan; zvono uvek). */
    ODRZAVA_SE,
    /** Predlozi ocena objavljeni ili ažurirani (push {@code push_ocene}, zvono {@code zvono_ocene}). */
    OCENE,
    /** Zbirno posle časa: prezentacija i novo za vežbanje (push {@code push_posle_casa}, zvono {@code zvono_posle_casa}). */
    POSLE_CASA,
    /** Anketa na času, čekiranima na predavanju (push {@code push_ankete}; nije u zvonu). */
    ANKETA_CAS,
    /** Anketa za grupu do roka (push {@code push_ankete}; zvono uvek). */
    ANKETA
}
