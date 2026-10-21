package tri.novica.gfssystem.service.prijava;

/**
 * Student je dostigao limit od 3 koda na sat (objavljuje {@link KodServis#posalji}). Obaveštenje nastavniku
 * ({@code PREVISE_KODOVA}) upisuje {@link ObavestenjeServis} u pozadini, posle odgovora, da 429 za postojećeg studenta
 * ne radi ništa više u bazi nego za nepostojeći indeks. Sadrži samo id studenta i IP.
 */
public record DostignutLimitKodova(Long studentId, String ip) {}
