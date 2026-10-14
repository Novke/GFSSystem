package tri.novica.gfssystem.service.uzivo;

/**
 * Stanje izvođenja se promenilo (komanda, tajmer, prijava, moderacija, izmena prezentacije). Objavljuje se unutar
 * transakcije; slušalac šalje nova stanja klijentima tek posle commit-a ({@code @TransactionalEventListener}).
 */
public record IzvodjenjePromenjeno(Long izvodjenjeId) {
}
