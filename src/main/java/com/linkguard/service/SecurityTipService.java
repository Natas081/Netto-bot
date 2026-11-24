package com.linkguard.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class SecurityTipService {

    static final List<String> TIPS = List.of(
            "Nunca compartilhe códigos de verificação recebidos por SMS ou WhatsApp.",
            "Confira o domínio completo antes de digitar sua senha. Um nome parecido pode esconder um site falso.",
            "Desconfie de mensagens que exigem urgência para evitar um bloqueio de conta.",
            "Use uma senha longa e exclusiva para cada conta. Um gerenciador de senhas pode ajudar.",
            "Ative a autenticação em dois fatores nas suas contas, especialmente no e-mail e no WhatsApp.",
            "Antes de fazer um PIX, confira o nome do destinatário e o valor na tela do seu banco.",
            "Recebeu um pedido de dinheiro de um conhecido? Confirme por uma ligação para o número que você já tem.",
            "Desconfie de promoções com preços muito abaixo do normal e pesquise a loja por conta própria.",
            "Um cadeado e HTTPS protegem a conexão, mas um site fraudulento também pode usá-los.",
            "Links encurtados escondem o endereço final. Confirme a origem da mensagem antes de continuar.",
            "Evite acessar o banco em redes Wi-Fi públicas. Prefira sua conexão móvel.",
            "Mantenha o sistema operacional, o navegador e seus aplicativos atualizados.",
            "Instale aplicativos pelas lojas oficiais e confira o desenvolvedor e as permissões solicitadas.",
            "Nunca instale um aplicativo enviado em um link por alguém que se apresenta como suporte do banco.",
            "Revise as configurações de privacidade das redes sociais e limite quem pode ver suas informações.",
            "Se uma senha vazar, altere-a imediatamente e troque-a também nas contas em que foi reutilizada.",
            "Nunca informe senha, token ou código de autenticação para um suposto atendente.",
            "Acesse serviços importantes pelos seus favoritos ou digitando o endereço conhecido.",
            "Desconfie de anexos inesperados, mesmo quando parecem ter sido enviados por alguém conhecido.",
            "Ative alertas de acesso e de transações para perceber atividades que você não reconhece.",
            "Faça cópias de segurança dos seus arquivos e mantenha uma cópia separada do dispositivo principal.",
            "Confira o endereço real do remetente de um e-mail. O nome exibido pode ser imitado.",
            "Não publique fotos de documentos, cartões ou comprovantes com dados pessoais.",
            "Em compras online, confirme os canais de contato e a reputação da loja antes de pagar.",
            "Revise os dispositivos conectados ao WhatsApp e encerre sessões que você não reconhece.",
            "Mensagens de sorteios que pedem um pagamento para liberar o prêmio são um sinal de alerta.",
            "Ao ler um QR Code para pagar, confira o destinatário na tela do aplicativo antes de confirmar.",
            "Bloqueie a tela do celular e do computador quando se afastar deles.",
            "Use os canais oficiais para confirmar mensagens sobre multas, entregas ou dívidas.",
            "Não envie a serviços de análise links privados, de recuperação de senha ou com tokens de acesso."
    );

    public String randomTip() {
        return TIPS.get(ThreadLocalRandom.current().nextInt(TIPS.size()));
    }
}
