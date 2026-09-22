package br.com.db.votacao.pauta;

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

import static br.com.db.votacao.pauta.PautaDtos.*;

@Service
public class SessaoService {
    private static final Logger log = LoggerFactory.getLogger(SessaoService.class);
    private final PautaService pautas;
    private final SessaoRepository sessoes;
    private final Clock clock;

    public SessaoService(PautaService pautas, SessaoRepository sessoes, Clock clock) {
        this.pautas = pautas;
        this.sessoes = sessoes;
        this.clock = clock;
    }

    @Transactional
    public SessaoResponse abrir(Long pautaId, AbrirSessao request) {
        Pauta pauta = pautas.buscarEntidade(pautaId);
        if (sessoes.existsByPautaId(pautaId)) {
            throw sessaoJaExiste();
        }
        int minutos = request == null ? 1 : request.duracaoEfetiva();
        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Sessao sessao = new Sessao(pauta, agora, agora.plus(minutos, ChronoUnit.MINUTES));
        try {
            sessao = sessoes.saveAndFlush(sessao);
        } catch (DataIntegrityViolationException exception) {
            // O SELECT anterior melhora a mensagem usual; só UNIQUE garante concorrência.
            if (ConstraintErrors.isConstraint(exception, "uq_sessao_pauta")) {
                throw sessaoJaExiste();
            }
            throw exception;
        }
        log.info("Sessão aberta: pautaId={}, sessaoId={}, encerraEm={}", pautaId, sessao.getId(), sessao.getEncerraEm());
        return SessaoResponse.from(sessao, agora);
    }

    private DomainException sessaoJaExiste() {
        return DomainException.conflito("SESSAO_JA_EXISTE", "Esta pauta já possui uma sessão. Não é permitido reabri-la.");
    }
}
