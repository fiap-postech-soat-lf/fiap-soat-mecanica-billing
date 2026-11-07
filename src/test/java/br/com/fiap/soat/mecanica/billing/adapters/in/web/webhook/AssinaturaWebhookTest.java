package br.com.fiap.soat.mecanica.billing.adapters.in.web.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Vetores calculados a parte, com openssl: printf '<manifesto>' | openssl dgst -sha256 -hmac '<segredo>'. */
class AssinaturaWebhookTest {

    static final String SEGREDO = "segredo-de-teste";
    static final String REQUEST_ID = "bb56a2f1-6aae-46ac-982e-9dcd3581d08e";
    static final String TS = "1704908010";
    static final String V1 = "860ac38339722866664f7a6b3cd9ceff16bfca9c96d5b3ceec99c42264da3875";
    static final String V1_SEM_ID = "a643f066fd7e1004753df1cac1839b19c89ff5fd32d93ed10c139a9af0f81fd1";

    final AssinaturaWebhook assinatura = new AssinaturaWebhook(SEGREDO);

    @Test
    void vetorConhecidoPassa() {
        assertThat(assinatura.valida("ts=" + TS + ",v1=" + V1, REQUEST_ID, "123456")).isTrue();
    }

    @Test
    void partesEmOrdemTrocadaEEspacosPassam() {
        assertThat(assinatura.valida("v1=" + V1 + ", ts=" + TS, REQUEST_ID, "123456")).isTrue();
    }

    @Test
    void dataIdEmMaiusculasENormalizado() {
        String v1 = "959d839cd04879dd3a5fae5977c2484d1c869553b8689c07a6ae09557221de2e";
        assertThat(assinatura.valida("ts=1,v1=" + v1, "r1", "ABC123")).isTrue();
        assertThat(assinatura.valida("ts=1,v1=" + v1, "r1", "abc123")).isTrue();
    }

    @Test
    void v1EmMaiusculasEAceito() {
        assertThat(assinatura.valida("ts=" + TS + ",v1=" + V1.toUpperCase(), REQUEST_ID, "123456")).isTrue();
    }

    @Test
    void umaParteAusenteSaiDoManifesto() {
        assertThat(AssinaturaWebhook.manifesto(null, REQUEST_ID, TS)).isEqualTo("request-id:" + REQUEST_ID + ";ts:" + TS + ";");
        assertThat(assinatura.valida("ts=" + TS + ",v1=" + V1_SEM_ID, REQUEST_ID, null)).isTrue();
        assertThat(assinatura.valida("ts=" + TS + ",v1=" + V1_SEM_ID, REQUEST_ID, " ")).isTrue();
    }

    @Test
    void v1ErradoFalha() {
        assertThat(assinatura.valida("ts=" + TS + ",v1=" + "0".repeat(64), REQUEST_ID, "123456")).isFalse();
        assertThat(assinatura.valida("ts=" + TS + ",v1=abc", REQUEST_ID, "123456")).isFalse();
    }

    @Test
    void tsAdulteradoFalha() {
        assertThat(assinatura.valida("ts=1704908011,v1=" + V1, REQUEST_ID, "123456")).isFalse();
    }

    @Test
    void dataIdDiferenteFalha() {
        assertThat(assinatura.valida("ts=" + TS + ",v1=" + V1, REQUEST_ID, "123457")).isFalse();
    }

    @Test
    void requestIdDiferenteFalha() {
        assertThat(assinatura.valida("ts=" + TS + ",v1=" + V1, "outro", "123456")).isFalse();
    }

    @Test
    void headerSemTsOuSemV1Falha() {
        assertThat(assinatura.valida("v1=" + V1, REQUEST_ID, "123456")).isFalse();
        assertThat(assinatura.valida("ts=" + TS, REQUEST_ID, "123456")).isFalse();
        assertThat(assinatura.valida("ts=,v1=" + V1, REQUEST_ID, "123456")).isFalse();
        assertThat(assinatura.valida("lixo,ts" + TS, REQUEST_ID, "123456")).isFalse();
    }

    @Test
    void headerAusenteFalha() {
        assertThat(assinatura.valida(null, REQUEST_ID, "123456")).isFalse();
        assertThat(assinatura.valida("", REQUEST_ID, "123456")).isFalse();
    }

    @Test
    void segredoVazioOuAusenteFalhaMesmoComAssinaturaDeSegredoVazio() {
        String v1DeSegredoVazio = java.util.HexFormat.of().formatHex(
                AssinaturaWebhook.calcular("x", AssinaturaWebhook.manifesto("123456", REQUEST_ID, TS)));
        assertThat(new AssinaturaWebhook("").valida("ts=" + TS + ",v1=" + v1DeSegredoVazio, REQUEST_ID, "123456")).isFalse();
        assertThat(new AssinaturaWebhook(null).valida("ts=" + TS + ",v1=" + V1, REQUEST_ID, "123456")).isFalse();
    }
}
