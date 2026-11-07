package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.webhook;

import br.com.fiap.soat.mecanica.billing.domain.webhook.WebhookEvento;
import br.com.fiap.soat.mecanica.billing.domain.webhook.WebhookEventoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class WebhookEventoJpaAdapter implements WebhookEventoRepository {

    private final WebhookEventoJpaRepository eventos;
    private final MpStatusAplicadoJpaRepository aplicados;

    public WebhookEventoJpaAdapter(WebhookEventoJpaRepository eventos, MpStatusAplicadoJpaRepository aplicados) {
        this.eventos = eventos;
        this.aplicados = aplicados;
    }

    @Override
    @Transactional
    public WebhookEvento salvar(WebhookEvento evento) {
        return paraDominio(eventos.saveAndFlush(paraEntity(evento)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<WebhookEvento> travarProximoPendente(Instant agora) {
        return eventos.travarProximoPendente(agora).map(WebhookEventoJpaAdapter::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean statusJaAplicado(String mpPaymentId, String status) {
        return aplicados.existsById(new MpStatusAplicadoId(mpPaymentId, status));
    }

    @Override
    @Transactional
    public void registrarStatusAplicado(String mpPaymentId, String status, UUID pagamentoId, Instant agora) {
        MpStatusAplicadoEntity entity = new MpStatusAplicadoEntity();
        entity.setMpPaymentId(mpPaymentId);
        entity.setStatus(status);
        entity.setPagamentoId(pagamentoId);
        entity.setAplicadoEm(agora);
        aplicados.saveAndFlush(entity);
    }

    private static WebhookEventoEntity paraEntity(WebhookEvento e) {
        WebhookEventoEntity entity = new WebhookEventoEntity();
        entity.setId(e.getId());
        entity.setRequestId(e.getRequestId());
        entity.setMpPaymentId(e.getMpPaymentId());
        entity.setTipo(e.getTipo());
        entity.setAcao(e.getAcao());
        entity.setRecebidoEm(e.getRecebidoEm());
        entity.setStatus(e.getStatus());
        entity.setTentativas(e.getTentativas());
        entity.setProximaTentativaEm(e.getProximaTentativaEm());
        entity.setStatusMp(e.getStatusMp());
        entity.setUltimoErro(e.getUltimoErro());
        return entity;
    }

    private static WebhookEvento paraDominio(WebhookEventoEntity e) {
        return new WebhookEvento(e.getId(), e.getRequestId(), e.getMpPaymentId(), e.getTipo(), e.getAcao(),
                e.getRecebidoEm(), e.getStatus(), e.getTentativas(), e.getProximaTentativaEm(), e.getStatusMp(),
                e.getUltimoErro());
    }
}
