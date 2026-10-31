package tri.novica.gfssystem.entity.prijava;

/**
 * Vrsta brojača u tabeli {@code pokusaji} (limiti koji preživljavaju restart); {@code kljuc} je IP, id studenta,
 * heš indeksa ili fiksna vrednost za ukupne limite, zavisno od vrste.
 */
public enum VrstaPokusaja {
    /** Neuspela prijava nastavnika, po IP-u. */
    NASTAVNIK_GRESKA_IP,
    /** Zahtev za kod, po IP-u (računa se i nepostojeći indeks). */
    KOD_SLANJE_IP,
    /** Poslat kod, po studentu. */
    KOD_SLANJE_STUDENT,
    /** Pogrešan kod, po IP-u. */
    KOD_GRESKA_IP,
    /** "Ne stiže mi kod", po IP-u. */
    POMOC_IP,
    /** "Ne stiže mi kod", po indeksu. */
    POMOC_INDEKS,
    /** Kod poslat sa kartice studenta (nastavnik), ukupno. */
    KOD_SA_KARTICE,
    /** Kod za diktiranje sa kartice studenta (nastavnik), ukupno. */
    KOD_DIKTIRAN,
    /** Mejl nastavniku, po (vrsta obaveštenja, indeks). */
    MEJL_NASTAVNIKU,
    /** Mejl nastavniku, ukupno. */
    MEJL_NASTAVNIKU_UKUPNO,
    /** Novo izvlačenje zadatka pri vežbanju na nalogu, po studentu (ponovo prikazan pokušaj se ne broji). */
    VEZBANJE_IZVLACENJE,
    /** Upis push pretplate, po uređaju studenta ({@code u:<id>}) ili sesiji nastavnika ({@code s:<id>}). */
    PUSH_PRETPLATA,
    /** Probno push obaveštenje, po uređaju ili sesiji (isti ključ). */
    PUSH_PROBA
}
