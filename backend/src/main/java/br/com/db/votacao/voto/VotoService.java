package br.com.db.votacao.voto;

import br.com.db.votacao.associado.ElegibilidadeService;
import br.com.db.votacao.pauta.Pauta;
import br.com.db.votacao.pauta.PautaService;
import br.com.db.votacao.pauta.Sessao;
import br.com.db.votacao.pauta.SessaoRepository;
import br.com.db.votacao.shared.ConstraintErrors;
import br.com.db.votacao.shared.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static br.com.db.votacao.voto.VotoDtos.*;

@Service
public class VotoService {
    private static final Logger log = LoggerFactory.getLogger(VotoService.class);
    private final PautaService pautas;
    private final SessaoRepository sessoes;
    private final VotoRepository votos;
    private final ElegibilidadeService elegibilidade;
    private final Clock clock;

    public VotoService(PautaService pautas, SessaoRepository sessoes, VotoRepository votos,
                       ElegibilidadeService elegibilidade, Clock clock) {
        this.pautas = pautas;
        this.sessoes = sessoes;
        this.votos = votos;
        this.elegibilidade = elegibilidade;
        this.clock = clock;
    }

    @Transactional
    public VotoResponse votar(Long pautaId, RegistrarVoto request) {
        Pauta pauta = pautas.buscarEntidade(pautaId);
        Sessao sessao = sessoes.findByPautaId(pautaId).orElseThrow(() ->
                DomainException.conflito("SESSAO_NAO_ABERTA", "Abra a sessão antes de receber votos."));
        exigirSessaoAberta(sessao, clock.instant());
        if (votos.existsByPautaIdAndAssociadoId(pautaId, request.associadoId())) {
            throw votoDuplicado();
        }
        if (request.cpf() != null) {
            elegibilidade.exigirApto(request.cpf());
        }
        // A consulta de elegibilidade pode demorar: revalidar o prazo e usar esse mesmo instante no voto.
        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        exigirSessaoAberta(sessao, agora);
        Voto voto = new Voto(pauta, request.associadoId(), request.voto(), agora);
        try {
            voto = votos.saveAndFlush(voto);
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintErrors.isConstraint(exception, "uq_voto_pauta_associado")) {
                throw votoDuplicado();
            }
            throw exception;
        }
        log.debug("Voto registrado: pautaId={}, votoId={}", pautaId, voto.getId());
        return VotoResponse.from(voto);
    }

    private void exigirSessaoAberta(Sessao sessao, Instant agora) {
        if (!sessao.estaAberta(agora)) {
            throw DomainException.conflito("SESSAO_ENCERRADA", "A sessão está encerrada e não aceita novos votos.");
        }
    }

    private DomainException votoDuplicado() {
        return DomainException.conflito("VOTO_DUPLICADO", "Este associado já votou nesta pauta.");
    }
}
