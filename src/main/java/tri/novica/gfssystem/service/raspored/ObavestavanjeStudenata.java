package tri.novica.gfssystem.service.raspored;

import tri.novica.gfssystem.entity.Student;

import java.util.List;

/**
 * Jedini put kojim aplikacija javlja studentima promenu termina (spec najave odsustva, odeljak 6). Danas je jedina
 * implementacija mejl ({@link MejlObavestavanjeStudenata}); push ili Android aplikacija dolaze kao nova implementacija ili
 * kompozicija iza istog interfejsa, bez izmene pozivalaca. Poziva se **posle** commit-a promene (van transakcije), pa
 * vraća tačan broj poruka stavljenih u red za slanje.
 */
public interface ObavestavanjeStudenata {

    /**
     * Ishod: {@code poslato} = poruke stavljene u red za slanje, {@code neposlato} = studenti sa adresom koji poruku
     * nisu dobili (pun red ili slanje nije podešeno).
     */
    record Ishod(int poslato, int neposlato) {
        public static final Ishod NISTA = new Ishod(0, 0);
    }

    /**
     * Javlja promenu svakom studentu sa liste kome ume da pošalje poruku (jedna poruka po adresi, nikad zajednička lista
     * primalaca; ista adresa dvaput na listi dobija jednu poruku).
     */
    Ishod obavesti(List<Student> studenti, PromenaTermina promena);
}
