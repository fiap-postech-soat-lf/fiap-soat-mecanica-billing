package br.com.fiap.soat.mecanica.billing.application.orcamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.security.DonoDoRecurso;
import br.com.fiap.soat.mecanica.billing.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BuscarOrcamentoUseCase {

    private final OrcamentoRepository orcamentos;
    private final UsuarioAutenticadoPort usuario;

    public BuscarOrcamentoUseCase(OrcamentoRepository orcamentos, UsuarioAutenticadoPort usuario) {
        this.orcamentos = orcamentos;
        this.usuario = usuario;
    }

    @Transactional(readOnly = true)
    public Orcamento executar(UUID id) {
        Orcamento orcamento = orcamentos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado: " + id));
        DonoDoRecurso.exigir(usuario, orcamento.getClienteId(), "Orcamento de outro cliente");
        return orcamento;
    }
}
