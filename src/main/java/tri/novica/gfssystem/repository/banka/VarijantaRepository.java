package tri.novica.gfssystem.repository.banka;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.entity.banka.Varijanta;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VarijantaRepository extends JpaRepository<Varijanta, Long>, JpaSpecificationExecutor<Varijanta> {

    /**
     * Varijante grupe po {@code rb} (pa id-ju), samo zadatog statusa kad nije {@code null}; SVG mediji u istom upitu
     * (bez upita po varijanti).
     */
    @Query("""
            select v from Varijanta v left join fetch v.renderSvg left join fetch v.renderResenjeSvg
            where v.grupa.id = :grupaId and (:status is null or v.status = :status) order by v.rb, v.id""")
    List<Varijanta> lista(@Param("grupaId") Long grupaId, @Param("status") StatusVarijante status);

    /** Samo id grupe, pre zaključavanja: red grupe se zaključava pre reda varijante, a entitet učitan pre ključa se ne osvežava. */
    @Query("select v.grupa.id from Varijanta v where v.id = :id")
    Optional<Long> findGrupaId(@Param("id") Long id);

    /** Najveći {@code rb} u grupi, 0 za praznu grupu (nova varijanta dobija sledeći). */
    @Query("select coalesce(max(v.rb), 0) from Varijanta v where v.grupa.id = :grupaId")
    int maxRb(@Param("grupaId") Long grupaId);

    /** Zaključava red varijante (SELECT ... FOR UPDATE) do kraja transakcije: izmene i render idu redom. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Varijanta v where v.id = :id")
    Optional<Varijanta> findByIdForUpdate(@Param("id") Long id);

    /** Sve varijante grupe, zaključane (posle reda grupe): promena šeme grupe i brisanje grupe. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Varijanta v where v.grupa.id = :grupaId order by v.rb, v.id")
    List<Varijanta> findByGrupaIdForUpdate(@Param("grupaId") Long grupaId);

    long countByGrupaId(Long grupaId);

    /** Brojači za stablo, jedan upit: redovi {@code [grupaId, status, renderStatus, broj]}. */
    @Query("""
            select v.grupa.id, v.status, v.renderStatus, count(v) from Varijanta v
            where v.grupa.id in :ids group by v.grupa.id, v.status, v.renderStatus""")
    List<Object[]> brojaciPoGrupi(@Param("ids") Collection<Long> ids);

    /** Klodovi nacrti (CLAUDE, NACRT) po grupi za stablo, jedan upit: redovi {@code [grupaId, broj]}, grupe bez njih nisu tu. */
    @Query("""
            select v.grupa.id, count(v) from Varijanta v
            where v.grupa.id in :ids and v.izvor = tri.novica.gfssystem.entity.banka.IzvorVarijante.CLAUDE
            and v.status = tri.novica.gfssystem.entity.banka.StatusVarijante.NACRT group by v.grupa.id""")
    List<Object[]> claudeNacrtiPoGrupi(@Param("ids") Collection<Long> ids);

    /**
     * Sve varijante predmeta (ponovni render cele banke posle izmene preambule) u jednom upitu: grupa sa precima (dubina
     * je najviše 6) i predmetom, oba SVG medija i prilozi sa medijima (sve EAGER veze, inače upit po varijanti i grupi).
     */
    @Query("""
            select distinct v from Varijanta v
            join fetch v.grupa g join fetch g.predmet
            left join fetch g.roditelj r1 left join fetch r1.roditelj r2 left join fetch r2.roditelj r3
            left join fetch r3.roditelj r4 left join fetch r4.roditelj r5
            left join fetch v.renderSvg left join fetch v.renderResenjeSvg
            left join fetch v.prilozi p left join fetch p.medij
            where g.predmet.id = :predmetId""")
    List<Varijanta> svePredmeta(@Param("predmetId") Long predmetId);

    /**
     * Brojači serija ({@code SerijaService}), jedan upit: redovi {@code [serija, Klodovih nacrta, ikad odobrenih, najnovije
     * kreirano]}; {@code predmetId == null} = svi predmeti.
     */
    @Query("""
            select v.serija,
            sum(case when v.izvor = tri.novica.gfssystem.entity.banka.IzvorVarijante.CLAUDE
              and v.status = tri.novica.gfssystem.entity.banka.StatusVarijante.NACRT then 1 else 0 end),
            sum(case when v.ikadOdobrena = true then 1 else 0 end), max(v.kreirano)
            from Varijanta v where v.serija is not null and (:predmetId is null or v.grupa.predmet.id = :predmetId)
            group by v.serija""")
    List<Object[]> brojaciPoSeriji(@Param("predmetId") Long predmetId);

    /**
     * Varijante serije za poništavanje, rastuće po id-ju: redovi {@code [id, rb, naziv, grupaId, naziv grupe, status, izvor,
     * ikadOdobrena]}. Samo vrednosti, ne entiteti: brisanje posle zaključava i čita varijantu samo, a entitet učitan pre
     * ključa ostao bi u persistence context-u sa stanjem od pre ključa (Novica je u međuvremenu mogao da odobri).
     */
    @Query("""
            select v.id, v.rb, v.naziv, g.id, g.naziv, v.status, v.izvor, v.ikadOdobrena
            from Varijanta v join v.grupa g where v.serija = :serija order by v.id""")
    List<Object[]> zaPonistavanje(@Param("serija") String serija);

    /**
     * AKTIVNE varijante sa gotovim SVG-om po grupi i nivou, jedan upit: redovi {@code [grupaId, nivo, broj]}, grupe bez
     * njih nisu tu (kandidati slajda Zadatak po nivou, {@code KandidatiZadatka}).
     */
    @Query("""
            select v.grupa.id, v.nivo, count(v) from Varijanta v
            where v.grupa.id in :ids and v.status = tri.novica.gfssystem.entity.banka.StatusVarijante.AKTIVNA
            and v.renderSvg is not null group by v.grupa.id, v.nivo""")
    List<Object[]> aktivneSaSvgPoGrupiINivou(@Param("ids") Collection<Long> ids);

    /**
     * AKTIVNE varijante zadatih grupa sa SVG-om teksta (inner join: bez njega varijanta nije kandidat) i, kad ga ima,
     * SVG rešenja, po grupi pa po id-ju; jedan upit bez upita po varijanti.
     */
    @Query("""
            select v from Varijanta v join fetch v.renderSvg left join fetch v.renderResenjeSvg
            where v.grupa.id in :ids and v.status = tri.novica.gfssystem.entity.banka.StatusVarijante.AKTIVNA
            order by v.grupa.id, v.id""")
    List<Varijanta> aktivneZaGrupe(@Param("ids") Collection<Long> ids);

    /**
     * Zaključava (SELECT ... FOR UPDATE, do kraja transakcije) zadate varijante koje su i dalje kandidati slajda Zadatak
     * (AKTIVNE sa SVG-om teksta) i vraća njihove id-jeve rastuće: obrisane i one koje su u međuvremenu izašle iz AKTIVNIH
     * nisu u rezultatu (uslov proverava baza nad poslednjom verzijom reda, posle čekanja na ključ). Zove ga samo otvaranje
     * runde ({@code ZadatakDodelaService.snimak}), posle zaključanog reda izvođenja. Nativan upit sa skalarnim rezultatom:
     * zaključava samo redove {@code varijante} po primarnom ključu, rastuće po id-ju (ne i redove grupa ili medija preko
     * EAGER veza) i ne vraća entitete (entitet učitan pre ključa se ne osvežava). {@code ids} ne sme biti prazan.
     */
    @Query(value = """
            select v.id from varijante v
            where v.id in (:ids) and v.status = 'AKTIVNA' and v.render_svg_id is not null
            order by v.id for update""", nativeQuery = true)
    List<Long> zakljucajKandidate(@Param("ids") Collection<Long> ids);

    /**
     * Kao {@link #zakljucajKandidate}, uz nivo slajda: varijanta kojoj je nivo promenjen ({@code PATCH .../nivo}) između
     * čitanja kandidata i ključa ispada kao arhivirana, pa runda nikad ne meša nivoe.
     */
    @Query(value = """
            select v.id from varijante v
            where v.id in (:ids) and v.status = 'AKTIVNA' and v.render_svg_id is not null and v.nivo = :nivo
            order by v.id for update""", nativeQuery = true)
    List<Long> zakljucajKandidateNivoa(@Param("ids") Collection<Long> ids, @Param("nivo") int nivo);

    /** Poništava keš rendera cele banke predmeta (preambula je ulaz heša); vraća broj izmenjenih varijanti. */
    @Modifying
    @Query("update Varijanta v set v.renderHash = null where v.grupa.predmet.id = :predmetId")
    int ponistiHashPredmeta(@Param("predmetId") Long predmetId);
}
