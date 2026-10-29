package tri.novica.gfssystem.dto.raspored;

/** Posle otkazivanja ili vraćanja: termin i koliko studenata će biti obavešteno (mejlom; 0 bez SMTP-a ili bez promene). */
public record PromenaTerminaOdgovor(TerminInfo termin, int obavesteno) {
}
