package tri.novica.gfssystem.entity.prijava;

/**
 * Kako je kod za prijavu stigao do studenta ({@code kodovi_za_prijavu.nacin}). Nov kod poništava samo ranije aktivne kodove
 * <b>iste</b> vrste, pa kod koji je nastavnik izdiktirao ne prestaje da važi kad student u međuvremenu traži kod na mejl
 * (i obrnuto); prijava bilo kojim kodom poništava sve ostale aktivne kodove studenta.
 */
public enum NacinKoda {
    /** Poslat na mejl studenta (forma za prijavu ili "Pošalji kod" sa kartice). */
    MEJL,
    /** Prikazan nastavniku na kartici studenta, da ga izdiktira ("Kod za diktiranje"); ne šalje se nikud. */
    NASTAVNIK
}
