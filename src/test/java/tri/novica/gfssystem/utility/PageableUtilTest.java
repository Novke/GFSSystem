package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PageableUtilTest {

    static final Set<String> POLJA = Set.of("datum", "rb", "tema");
    static final Sort PODRAZUMEVANI = Sort.by(Sort.Order.desc("datum"), Sort.Order.desc("rb"));

    @Test
    void prazanSortDajePodrazumevani() {
        Pageable p = PageableUtil.proveri(PageRequest.of(2, 25), POLJA, PODRAZUMEVANI);
        assertEquals(List.of(Sort.Order.desc("datum"), Sort.Order.desc("rb"), Sort.Order.desc("id")),
                p.getSort().toList());
    }

    @Test
    void dozvoljenoPoljeProlazi() {
        Pageable p = PageableUtil.proveri(PageRequest.of(0, 10, Sort.by(Sort.Order.asc("datum"))), POLJA, PODRAZUMEVANI);
        assertEquals(Sort.Order.asc("datum"), p.getSort().toList().get(0));
    }

    @Test
    void stranicaIVelicinaSeCuvaju() {
        Pageable p = PageableUtil.proveri(PageRequest.of(3, 50, Sort.by("tema")), POLJA, PODRAZUMEVANI);
        assertEquals(3, p.getPageNumber());
        assertEquals(50, p.getPageSize());
        p = PageableUtil.proveri(PageRequest.of(4, 10), POLJA, PODRAZUMEVANI);
        assertEquals(4, p.getPageNumber());
        assertEquals(10, p.getPageSize());
    }

    @Test
    void nepoznatoPoljeJe400() {
        SystemException ex = assertThrows(SystemException.class, () ->
                PageableUtil.proveri(PageRequest.of(0, 25, Sort.by(Sort.Order.asc("lozinka"))), POLJA, PODRAZUMEVANI));
        assertEquals(400, ex.getCode());
        assertEquals("Neispravan parametar: sort.", ex.getMessage());
    }

    @Test
    void nepoznatoPoljeIzaDozvoljenogJeIsto400() {
        Sort sort = Sort.by(Sort.Order.asc("datum"), Sort.Order.asc("predmet.naziv"));
        SystemException ex = assertThrows(SystemException.class, () ->
                PageableUtil.proveri(PageRequest.of(0, 25, sort), POLJA, PODRAZUMEVANI));
        assertEquals("Neispravan parametar: sort.", ex.getMessage());
    }

    @Test
    void idJeUvekPoslednjiKriterijumZaStabilnoStraničenje() {
        Pageable p = PageableUtil.proveri(PageRequest.of(0, 25, Sort.by(Sort.Order.asc("tema"))), POLJA, PODRAZUMEVANI);
        assertEquals(List.of(Sort.Order.asc("tema"), Sort.Order.desc("id")), p.getSort().toList());
    }

    @Test
    void idJeUvekDozvoljenINeDodajeSeDvaput() {
        Pageable prvi = PageableUtil.proveri(PageRequest.of(0, 25), POLJA, PODRAZUMEVANI);
        Pageable drugi = PageableUtil.proveri(prvi, POLJA, PODRAZUMEVANI);
        assertEquals(prvi.getSort(), drugi.getSort());
        Pageable poId = PageableUtil.proveri(PageRequest.of(0, 25, Sort.by(Sort.Order.asc("id"))), POLJA, PODRAZUMEVANI);
        assertEquals(List.of(Sort.Order.asc("id")), poId.getSort().toList());
    }

    @Test
    void ofsetPrekoIntegerOpsegaJe400() {
        // Hibernate prima ofset kao int: bez ove provere page=99999999 bi dao 500
        SystemException ex = assertThrows(SystemException.class, () ->
                PageableUtil.proveri(PageRequest.of(99_999_999, 100), POLJA, PODRAZUMEVANI));
        assertEquals(400, ex.getCode());
        assertEquals("Neispravan parametar: page.", ex.getMessage());
    }
}
