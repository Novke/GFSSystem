package tri.novica.gfssystem.service.uzivo;

/**
 * Učesnik je odgovorio (objavljuje se unutar transakcije); slušalac posle commit-a šalje njegovo lično stanje i
 * označi nastavničko stanje za slanje.
 */
public record OdgovorPrimljen(Long izvodjenjeId, Long ucesnikId) {
}
