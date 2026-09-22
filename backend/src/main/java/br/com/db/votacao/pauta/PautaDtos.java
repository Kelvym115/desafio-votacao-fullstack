package br.com.db.votacao.pauta;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PautaDtos {
    private PautaDtos() { }

    public record CriarPauta(
            @NotBlank(message = "Informe o título da pauta.")
            @Size(max = 140, message = "O título deve ter no máximo 140 caracteres.")
            @Schema(example = "Aprovar novo horário da assembleia") String titulo,
            @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres.")
            String descricao) {
        public CriarPauta {
            titulo = titulo == null ? null : titulo.strip();
            descricao = descricao == null ? null : descricao.strip();
        }
    }

    public record AbrirSessao(
            @Min(value = 1, message = "A duração deve ser de pelo menos 1 minuto.")
            @Max(value = 1440, message = "A duração deve ser de no máximo 1440 minutos.")
            @Schema(example = "5", description = "Minutos inteiros, de 1 a 1440. Ausente ou null: 1 minuto.")
            Integer duracaoMinutos) {
        public int duracaoEfetiva() {
            return duracaoMinutos == null ? 1 : duracaoMinutos;
        }
    }

    public record PautaResponse(Long id, String titulo, String descricao, Instant criadaEm, SessaoResponse sessao) {
        public static PautaResponse from(Pauta pauta, Instant agora) {
            return new PautaResponse(pauta.getId(), pauta.getTitulo(), pauta.getDescricao(), pauta.getCriadaEm(),
                    pauta.getSessao() == null ? null : SessaoResponse.from(pauta.getSessao(), agora));
        }
    }

    public record SessaoResponse(Long id, Instant abertaEm, Instant encerraEm, String status) {
        public static SessaoResponse from(Sessao sessao, Instant agora) {
            return new SessaoResponse(sessao.getId(), sessao.getAbertaEm(), sessao.getEncerraEm(),
                    sessao.estaAberta(agora) ? "ABERTA" : "ENCERRADA");
        }
    }

    public record PaginaResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }
}
