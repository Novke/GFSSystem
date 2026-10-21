package tri.novica.gfssystem.security;

/**
 * Klasa pristupa endpointa (spec 2.3, 3): ANONIMNO bilo ko, STUDENT kolačić {@code gfs_student}, MCP sopstveni lanac
 * (ključ proverava MCP filter), ADMIN kolačić {@code gfs_nastavnik}. ADMIN je sve što {@link PristupMatrica} ne svrsta.
 */
public enum KlasaPristupa {
    ANONIMNO, STUDENT, MCP, ADMIN
}
