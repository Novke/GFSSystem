package tri.novica.gfssystem.service.uzivo;

/**
 * Odgovor učesnika nije primljen; poruka ide učesniku ({@code /user/queue/greske}) kako jeste. Bez stack trace-a:
 * odbijanje je očekivan ishod (dupli dodir, zakasneli odgovor), ne greška.
 */
public class OdgovorOdbijen extends RuntimeException {

    public OdgovorOdbijen(String poruka) {
        super(poruka, null, false, false);
    }
}
