package tri.novica.gfssystem.service.raspored;

import tri.novica.gfssystem.entity.Student;

import java.util.List;

/**
 * Jedini put kojim aplikacija javlja studentima promenu termina (spec najave odsustva, odeljak 6). Implementacije: mejl
 * ({@link MejlObavestavanjeStudenata}) i, od faze 4, kompozicija mejl + push ({@code service.push.MejlIPushObavestavanje},
 * {@code @Primary}), bez izmene pozivalaca. Poziva se **posle** commit-a promene (van transakcije), pa vraća tačan broj
 * poruka stavljenih u red za slanje.
 */
public interface ObavestavanjeStudenata {

    /**
     * Ishod: {@code poslato} = mejlovi stavljeni u red za slanje, {@code neposlato} = studenti sa adresom koji mejl
     * nisu dobili (pun red ili slanje nije podešeno), {@code pushPoslato} = push poruke stavljene u red (jedna po
     * pretplaćenom uređaju studenta).
     */
    record Ishod(int poslato, int neposlato, int pushPoslato) {
        public static final Ishod NISTA = new Ishod(0, 0, 0);

        /** Samo mejl (bez push-a). */
        public Ishod(int poslato, int neposlato) {
            this(poslato, neposlato, 0);
        }
    }

    /**
     * Javlja promenu svakom studentu sa liste kome ume da pošalje poruku (jedna poruka po adresi, nikad zajednička lista
     * primalaca; ista adresa dvaput na listi dobija jednu poruku).
     */
    Ishod obavesti(List<Student> studenti, PromenaTermina promena);
}
