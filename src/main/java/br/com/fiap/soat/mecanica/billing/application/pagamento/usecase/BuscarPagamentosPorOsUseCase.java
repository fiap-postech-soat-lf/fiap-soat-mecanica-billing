package br.com.fiap.soat.mecanica.billing.application.pagamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.security.DonoDoRecurso;
import br.com.fiap.soat.mecanica.billing.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** O pagamento vigente da OS, com o link, e o historico das cobrancas (F6-h, F6-k). */
@Service
public class BuscarPagamentosPorOsUseCase {

    public record Resultado(Pagamento vigente, List<Pagamento> historico) {
    }

    private final PagamentoRepository pagamentos;
    private final UsuarioAutenticadoPort usuario;

    public BuscarPagamentosPorOsUseCase(PagamentoRepository pagamentos, UsuarioAutenticadoPort usuario) {
        this.pagamentos = pagamentos;
        this.usuario = usuario;
    }

    @Transactional(readOnly = true)
    public Resultado executar(UUID osId) {
        List<Pagamento> historico = pagamentos.listarPorOs(osId);
        Pagamento vigente = historico.stream().filter(Pagamento::isVigente).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento nao encontrado para a OS: " + osId));
        DonoDoRecurso.exigir(usuario, vigente.getClienteId(), "Pagamento de outro cliente");
        return new Resultado(vigente, historico);
    }
}
