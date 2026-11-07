package br.com.fiap.soat.mecanica.billing.application.orcamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.orcamento.EventosOrcamento;
import br.com.fiap.soat.mecanica.billing.application.security.DonoDoRecurso;
import br.com.fiap.soat.mecanica.billing.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.billing.config.observability.BusinessEventLogger;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** CLIENTE so no proprio orcamento; ATENDENTE em nome do cliente (F6-e). */
@Service
public class AprovarOrcamentoUseCase {

    private final OrcamentoRepository orcamentos;
    private final UsuarioAutenticadoPort usuario;
    private final EventosOrcamento eventos;
    private final Clock clock;

    public AprovarOrcamentoUseCase(OrcamentoRepository orcamentos, UsuarioAutenticadoPort usuario,
                                   EventosOrcamento eventos, Clock clock) {
        this.orcamentos = orcamentos;
        this.usuario = usuario;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional
    public Orcamento executar(UUID id) {
        Orcamento orcamento = orcamentos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado: " + id));
        DonoDoRecurso.exigir(usuario, orcamento.getClienteId(), "Orcamento de outro cliente");

        StatusOrcamento de = orcamento.getStatus();
        UUID sub = usuario.sub();
        orcamento.aprovar(sub, clock.instant());
        Orcamento salvo = orcamentos.salvar(orcamento);

        eventos.aprovado(salvo, sub);
        BusinessEventLogger.orcamentoStatus(salvo.getId(), salvo.getOsId(), de, salvo.getStatus(), null);
        return salvo;
    }
}
