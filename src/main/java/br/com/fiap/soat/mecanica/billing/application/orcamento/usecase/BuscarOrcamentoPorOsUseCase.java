package br.com.fiap.soat.mecanica.billing.application.orcamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.security.DonoDoRecurso;
import br.com.fiap.soat.mecanica.billing.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** O orcamento vivo da OS ou, sem ele, o mais recente (F6-k). */
@Service
public class BuscarOrcamentoPorOsUseCase {

    private final OrcamentoRepository orcamentos;
    private final UsuarioAutenticadoPort usuario;

    public BuscarOrcamentoPorOsUseCase(OrcamentoRepository orcamentos, UsuarioAutenticadoPort usuario) {
        this.orcamentos = orcamentos;
        this.usuario = usuario;
    }

    @Transactional(readOnly = true)
    public Orcamento executar(UUID osId) {
        Orcamento orcamento = orcamentos.buscarVivoPorOs(osId)
                .or(() -> orcamentos.buscarMaisRecentePorOs(osId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado para a OS: " + osId));
        DonoDoRecurso.exigir(usuario, orcamento.getClienteId(), "Orcamento de outro cliente");
        return orcamento;
    }
}
