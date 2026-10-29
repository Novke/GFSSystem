package tri.novica.gfssystem.service.raspored;

import tri.novica.gfssystem.entity.Student;

import java.util.List;

/**
 * Jedini put kojim aplikacija javlja studentima promenu termina (spec najave odsustva, odeljak 6). Danas je jedina
 * implementacija mejl ({@link MejlObavestavanjeStudenata}); push ili Android aplikacija dolaze kao nova implementacija ili
 * kompozicija iza istog interfejsa, bez izmene pozivalaca. Poziva se u transakciji promene: slanje kreće tek posle commit-a.
 */
public interface ObavestavanjeStudenata {

    /**
     * Javlja promenu svakom studentu sa liste kome ume da pošalje poruku (jedna poruka po studentu, nikad zajednička lista
     * primalaca).
     *
     * @return koliko studenata će biti obavešteno
     */
    int obavesti(List<Student> studenti, PromenaTermina promena);
}
