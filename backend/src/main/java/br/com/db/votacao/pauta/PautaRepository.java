package br.com.db.votacao.pauta;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PautaRepository extends JpaRepository<Pauta, Long> {
    @Override
    @EntityGraph(attributePaths = "sessao")
    Optional<Pauta> findById(Long id);

    @EntityGraph(attributePaths = "sessao")
    Page<Pauta> findByTituloContainingIgnoreCase(String titulo, Pageable pageable);
}
