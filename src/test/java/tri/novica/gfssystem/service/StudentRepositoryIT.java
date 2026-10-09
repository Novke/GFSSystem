package tri.novica.gfssystem.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.repository.StudentRepository;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code postojiStudent} i {@code postojiDrugiStudent} nad pravom MySQL bazom: normalizovan indeks (velika slova, bez
 * razmaka) se poredi i sa starim redovima koji su sačuvani kako ih je ukucao nastavnik.
 */
@SpringBootTest
@Transactional
class StudentRepositoryIT {

    @Autowired EntityManager em;
    @Autowired StudentRepository repository;

    Grupa grupa;

    @BeforeEach
    void seed() {
        grupa = new Grupa();
        grupa.setNaziv("SR-A");
        grupa.setGodinaUpisa(2025);
        em.persist(grupa);
    }

    private Student student(String indeks, int godina) {
        Student s = new Student();
        s.setIme("Ime");
        s.setPrezime("Prezime");
        s.setIndeks(indeks);
        s.setGodina(godina);
        s.setGrupa(grupa);
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    void sopstveniRedJeIskljucen() {
        Student s = student("SRX1", 2025);

        assertTrue(repository.postojiStudent("SRX1", 2025));
        assertFalse(repository.postojiDrugiStudent("SRX1", 2025, s.getId()));
    }

    @Test
    void drugiStudentSaIstimIndeksomIGodinomJePronadjen() {
        Student s = student("SRX2", 2025);
        Student drugi = student("SRX2", 2025);

        assertTrue(repository.postojiDrugiStudent("SRX2", 2025, s.getId()));
        assertTrue(repository.postojiDrugiStudent("SRX2", 2025, drugi.getId()));
    }

    @Test
    void istiIndeksDrugeGodineNijeDuplikat() {
        Student s = student("SRX3", 2024);

        assertFalse(repository.postojiStudent("SRX3", 2025));
        assertFalse(repository.postojiDrugiStudent("SRX3", 2025, s.getId() + 1000));
        assertTrue(repository.postojiStudent("SRX3", 2024));
    }

    @Test
    void stariRedSaMalimSlovimaIRazmakomOdgovaraNormalizovanomIndeksu() {
        Student s = student("sr x4", 2025);

        assertTrue(repository.postojiStudent("SRX4", 2025));
        assertTrue(repository.postojiDrugiStudent("SRX4", 2025, s.getId() + 1000));
        assertFalse(repository.postojiDrugiStudent("SRX4", 2025, s.getId()));
    }
}
