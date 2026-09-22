package br.com.db.votacao.pauta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessaoRepository extends JpaRepository<Sessao, Long> {
    Optional<Sessao> findByPautaId(Long pautaId);
    boolean existsByPautaId(Long pautaId);
}
