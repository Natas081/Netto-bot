# LinkGuard Bot

Protótipo universitário de um chat que recebe URLs e simula sua classificação em confiável, suspeito ou perigoso. O Spring Boot serve a interface e a API pelo mesmo endereço.

**Os resultados são simulados e não representam uma análise real de segurança.** A aplicação valida o formato e inspeciona o texto da URL sem abrir o endereço enviado.

## Executar pelo terminal

Instale o **JDK 21** e confira a versão com `java -version`. Se houver várias versões do Java, configure `JAVA_HOME` para o JDK 21. O Maven Wrapper incluído baixa o Maven e as dependências na primeira execução. A partir da pasta que contém o `pom.xml`, execute o comando do seu sistema.

**Windows (PowerShell)**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux ou macOS**

```sh
sh ./mvnw spring-boot:run
```

Com Maven instalado, também é possível usar `mvn spring-boot:run`. Abra [localhost:8080](http://localhost:8080) quando o servidor iniciar. Use `Ctrl+C` no terminal para encerrar.

## Executar pelo IntelliJ IDEA

1. Em **File → Open**, selecione o arquivo `pom.xml` e abra-o como projeto Maven.
2. Em **File → Project Structure → Project**, configure o **Project SDK** para Java 21 e aguarde a importação do Maven.
3. Abra `src/main/java/com/linkguard/LinkGuardApplication.java` e use **Run 'LinkGuardApplication.main()'**.
4. Abra [localhost:8080](http://localhost:8080) no navegador.

## Testar no chat

Cole um dos endereços abaixo no campo da conversa ou selecione um dos exemplos disponíveis na página. Envie com **Enter** ou com o botão **Enviar**.

| URL de exemplo | Resultado esperado |
| --- | --- |
| `https://site-seguro.com` | CONFIÁVEL |
| `https://site-suspeito.com` | SUSPEITO |
| `https://site-perigoso.com` | PERIGOSO |

As palavras-chave são buscadas na URL inteira, independentemente de maiúsculas e minúsculas. Quando duas regras coincidem, a ordem de prioridade é `perigoso`, `suspeito`, `seguro`. Para URLs sem essas palavras, o serviço usa o hash do texto em minúsculas para escolher uma das três classificações. A mesma entrada sempre gera a mesma resposta.

O chat aceita endereços HTTP ou HTTPS com host válido e até 2.048 caracteres, inclusive com domínios internacionalizados. Envie `Olá` para testar a mensagem de validação.

## API

`POST /api/chat` recebe `Content-Type: application/json` com o formato abaixo.

```json
{
  "message": "https://site-suspeito.com"
}
```

Uma URL válida retorna HTTP 200 com a classificação e a resposta simulada.

```json
{
  "status": "SUSPEITO",
  "reply": "Atenção! Este link foi classificado como suspeito na simulação."
}
```

Uma mensagem vazia, longa demais ou inválida retorna HTTP 400 com `status` igual a `INVALIDO` e uma orientação em `reply`.

## Testes e compilação

No Windows, execute `.\mvnw.cmd test` para rodar os testes e `.\mvnw.cmd package` para gerar o JAR executável com os testes incluídos no build. No Linux ou macOS, substitua `.\mvnw.cmd` por `sh ./mvnw`.

```sh
java -jar target/linkguard-bot-0.0.1-SNAPSHOT.jar
```

Os testes cobrem as três classificações, a precedência das palavras-chave, a regra determinística, entradas inválidas, o limite de tamanho e o contrato de resposta do endpoint.

## Estrutura

- `controller/ChatController.java` recebe as requisições e define os códigos HTTP.
- `service/LinkAnalysisService.java` valida o texto e aplica as regras simuladas.
- `dto/ChatRequest.java` e `dto/ChatResponse.java` definem o JSON da API.
- `src/main/resources/static/` contém o HTML, o CSS e o JavaScript do chat.
- `src/test/java/` contém os testes de serviço e do endpoint.

Em uma próxima etapa, a classificação em `LinkAnalysisService` poderá ser substituída por um serviço de reputação de URLs. O controlador e o chat já se comunicam por um contrato JSON que pode ser reutilizado.
