package tri.novica.gfssystem.service.lti;

/**
 * Kodovi grešaka ulaska iz Moodle-a (spec 8.3): backend preusmerava na {@code /nalog/moodle?greska=<kod>}, a front ima
 * poruku za svaki kod. Detalj ide samo u log (bez claim vrednosti).
 */
public enum LtiGreska {
    /** Nema aktivne registracije za ({@code iss}, {@code client_id}, {@code lti_deployment_id}). */
    NEPOZNATA_REGISTRACIJA("nepoznata-registracija"),
    /** {@code state} ne postoji, istekao je, već je iskorišćen ili ga ovaj pregledač nije započeo. */
    ISTEKLA_SESIJA("istekla-sesija"),
    /** Launch bez kolačića {@code __Host-gfs_lti} (pregledač ne vraća {@code SameSite=None}); provera se ne slabi. */
    BEZ_KOLACICA("bez-kolacica"),
    /** Potpis, izdavač, {@code aud}/{@code azp}, vreme, {@code nonce}, {@code deployment_id} ili {@code sub} ne valja. */
    NEISPRAVAN_TOKEN("neispravan-token"),
    /** Druga vrsta poruke ili verzija (npr. deep linking), nedostaje login hint ili uloga koju GFS ne zna. */
    NEPODRZANA_PORUKA("nepodrzana-poruka");

    private final String kod;

    LtiGreska(String kod) {
        this.kod = kod;
    }

    public String kod() {
        return kod;
    }

    /** Relativno preusmerenje na stranicu fronta sa porukom za ovaj kod. */
    public String location() {
        return "/nalog/moodle?greska=" + kod;
    }
}
