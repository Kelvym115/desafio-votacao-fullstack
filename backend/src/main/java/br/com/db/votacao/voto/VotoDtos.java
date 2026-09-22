package br.com.db.votacao.voto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class VotoDtos {
    private VotoDtos() { }

    public record RegistrarVoto(
            @NotBlank(message = "Informe a identificação do associado.")
            @Size(max = 64, message = "A identificação deve ter no máximo 64 caracteres.")
            @Schema(example = "associado-123") String associadoId,
            @NotNull(message = "Informe SIM ou NAO.") OpcaoVoto voto,
            @Schema(description = "Opcional. Aciona o cliente fake de elegibilidade; use 11 dígitos fictícios.") String cpf) {
        public RegistrarVoto {
            associadoId = associadoId == null ? null : associadoId.strip();
        }
    }

    public record VotoResponse(Long id, Long pautaId, String associadoId, OpcaoVoto voto, Instant registradoEm) {
        public static VotoResponse from(Voto voto) {
            return new VotoResponse(voto.getId(), voto.getPauta().getId(), voto.getAssociadoId(), voto.getVoto(), voto.getRegistradoEm());
        }
    }

    public record ResultadoResponse(Long pautaId, long sim, long nao, long total, String status, String resultado) { }
}
