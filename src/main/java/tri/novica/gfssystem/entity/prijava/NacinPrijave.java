package tri.novica.gfssystem.entity.prijava;

/**
 * Kako je studentski uređaj prijavljen: kodom sa mejla, ulaskom iz Moodle-a (LTI) ili kao računar u učionici
 * ({@code RACUNAR}: QR odobren sa telefona ili kod na strani {@code /r}; traje {@code gfs.nalog.racunar-trajanje} od
 * prijave i ne produžava se). Stara slika ne čita {@code RACUNAR}: pre vraćanja preko V29 obrisati te uređaje.
 */
public enum NacinPrijave {
    KOD,
    LTI,
    RACUNAR
}
