package tri.novica.gfssystem.service.prijava;

import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;
import tri.novica.gfssystem.service.raspored.TerminOpis;

/**
 * Događaj: u transakciji je upisano novo nastavničko obaveštenje ({@code obavestenja}). Posle commit-a ga čita push
 * nastavniku ({@code service.push.PushNastavniku}). {@code termin}, {@code broj} i {@code prag} postoje samo za
 * {@code MNOGO_NAJAVA}; nikad ime studenta ni razlog.
 */
public record NovoObavestenje(VrstaObavestenja vrsta, TerminOpis termin, long broj, int prag) {
}
