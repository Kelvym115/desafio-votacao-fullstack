package br.com.db.votacao.pauta;

import br.com.db.votacao.shared.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static br.com.db.votacao.pauta.PautaDtos.*;

@Service
@Transactional(readOnly = true)
public class PautaService {
    private static final Logger log = LoggerFactory.getLogger(PautaService.class);
    private final PautaRepository pautas;
    private final Clock clock;

    public PautaService(PautaRepository pautas, Clock clock) {
        this.pautas = pautas;
        this.clock = clock;
    }

    @Transactional
    public PautaResponse criar(CriarPauta request) {
        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Pauta pauta = pautas.save(new Pauta(request.titulo(), request.descricao(), agora));
        log.info("Pauta criada: pautaId={}", pauta.getId());
        return PautaResponse.from(pauta, agora);
    }

    public PaginaResponse<PautaResponse> listar(int page, int size, String busca) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "criadaEm", "id"));
        Page<Pauta> pagina = pautas.findByTituloContainingIgnoreCase(busca == null ? "" : busca.strip(), pageable);
        Instant agora = clock.instant();
        return new PaginaResponse<>(pagina.getContent().stream().map(pauta -> PautaResponse.from(pauta, agora)).toList(),
                pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    public PautaResponse buscar(Long id) {
        return PautaResponse.from(buscarEntidade(id), clock.instant());
    }

    public Pauta buscarEntidade(Long id) {
        return pautas.findById(id).orElseThrow(DomainException::pautaNaoEncontrada);
    }
}
