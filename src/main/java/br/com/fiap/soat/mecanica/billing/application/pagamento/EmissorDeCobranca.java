package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.application.pagamento.port.GatewayPagamentoPort;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.Preferencia;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Monta uma cobranca nova (F6-g): o {@link Pagamento} PENDENTE e a preferencia do Checkout Pro, cobrando o
 * valor do orcamento aprovado. Nao grava nada: quem chama confirma o pagamento junto com a sua outbox, depois
 * de todas as validacoes locais, para uma falha de regra nunca criar preferencia e uma falha do Mercado Pago
 * nunca deixar um pagamento sem link.
 */
@Component
public class EmissorDeCobranca {

    static final String LINK_SANDBOX = "sandbox_init_point";

    private final GatewayPagamentoPort gateway;
    private final Clock clock;
    private final String link;

    public EmissorDeCobranca(GatewayPagamentoPort gateway, Clock clock,
                             @Value("${app.mercadopago.link:init_point}") String link) {
        this.gateway = gateway;
        this.clock = clock;
        this.link = link;
    }

    /** @param orcamento o orcamento APROVADO da OS: dele vem o valor, o dono (clienteId) e a OS */
    public Pagamento emitir(Orcamento orcamento) {
        Pagamento pagamento = Pagamento.criar(orcamento.getOsId(), orcamento.getId(), orcamento.getClienteId(),
                orcamento.getValorTotal(), clock.instant());
        Preferencia preferencia = gateway.criarPreferencia(pagamento.getId(), titulo(orcamento),
                orcamento.getValorTotal());
        pagamento.registrarPreferencia(preferencia.id(), escolher(preferencia));
        return pagamento;
    }

    static String titulo(Orcamento orcamento) {
        return "Ordem de serviço " + orcamento.getOsId().toString().substring(0, 8);
    }

    private String escolher(Preferencia preferencia) {
        boolean sandbox = LINK_SANDBOX.equals(link) && preferencia.sandboxInitPoint() != null;
        return sandbox ? preferencia.sandboxInitPoint() : preferencia.initPoint();
    }
}
