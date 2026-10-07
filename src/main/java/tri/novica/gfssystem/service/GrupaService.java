package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.grupa.CreateGrupaCmd;
import tri.novica.gfssystem.dto.grupa.GrupaDetails;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.grupa.UpdateGrupaCmd;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.utility.Brojaci;
import tri.novica.gfssystem.utility.Utility;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GrupaService {

    private final GrupaRepository grupaRepository;
    private final StudentRepository studentRepository;
    private final ModelMapper mapper;

    public List<GrupaInfo> findAll() {
        return grupaRepository.findAll().stream()
                .map(grupa -> {
                    GrupaInfo info = mapper.map(grupa, GrupaInfo.class);
                    info.setBrojStudenata(studentRepository.countByGrupaId(grupa.getId()));
                    return info;
                }).toList();
    }

    /**
     * Grupe čiji naziv sadrži {@code q} (bez obzira na velika i mala slova), najnovije generacije prve, sa brojem
     * studenata (jedan agregatni upit). Za globalnu pretragu.
     */
    public List<GrupaInfo> pretraga(String q, int najvise) {
        List<Grupa> grupe = grupaRepository.findByNazivContainingIgnoreCase(q, PageRequest.of(0, najvise,
                Sort.by(Sort.Order.desc("godinaUpisa"), Sort.Order.asc("naziv"), Sort.Order.desc("id"))));
        Map<Long, Long> broj = Brojaci.poId(grupe.stream().map(Grupa::getId).toList(),
                studentRepository::brojStudenataPoGrupi);
        return grupe.stream()
                .map(g -> new GrupaInfo(g.getId(), g.getNaziv(), g.getGodinaUpisa(), broj.getOrDefault(g.getId(), 0L)))
                .toList();
    }

    public GrupaInfo save(CreateGrupaCmd grupaCmd){
        String naziv = grupaCmd.getNaziv() == null ? null : grupaCmd.getNaziv().trim();
        if (naziv != null && grupaRepository.existsByNazivIgnoreCase(naziv)) {
            throw nazivZauzet(naziv);
        }
        Grupa grupa = mapper.map(grupaCmd, Grupa.class);
        grupa.setNaziv(naziv);
        return mapper.map(grupaRepository.save(grupa), GrupaInfo.class);
    }

    /** Izmena naziva i godine upisa; naziv ne sme da pripada nekoj drugoj grupi (sopstveni je dozvoljen). */
    public GrupaInfo update(Long id, UpdateGrupaCmd cmd) {
        Grupa grupa = grupaRepository.findById(id)
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + id, HttpStatus.NOT_FOUND));
        String naziv = cmd.getNaziv().trim();
        if (grupaRepository.existsByNazivIgnoreCaseAndIdNot(naziv, id)) {
            throw nazivZauzet(naziv);
        }
        grupa.setNaziv(naziv);
        grupa.setGodinaUpisa(cmd.getGodinaUpisa());
        GrupaInfo info = mapper.map(grupaRepository.save(grupa), GrupaInfo.class);
        info.setBrojStudenata(studentRepository.countByGrupaId(id));
        return info;
    }

    private static SystemException nazivZauzet(String naziv) {
        return new SystemException("Grupa sa nazivom " + naziv + " već postoji.", HttpStatus.BAD_REQUEST);
    }

    public GrupaDetails findById(Long id) {
        //moze i findById jer ce zbog mappera pozvati getStudenti ali je ovako bolje zbog efikasnijeg upita
        Grupa grupa = grupaRepository.findByIdFetchStudents(id).orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + id, HttpStatus.NOT_FOUND));

        //sortiraj po indeksu
        grupa.getStudenti().sort(
                Comparator.comparingInt(student ->
                        Utility.index2int(student.getIndeks())));

        return mapper.map(grupa, GrupaDetails.class);
    }
}
