# Netto

Netto é um assistente de segurança digital para um projeto universitário. O chat consulta relatórios do VirusTotal v3, solicita uma análise quando precisa de dados atualizados e acompanha sua conclusão automaticamente.

O projeto usa Java 21, Spring Boot 3.5, Maven, Spring Web e o RestClient do Spring. HTML, CSS, JavaScript e a fonte Inter são servidos pelo próprio Spring Boot.

## Funcionalidades

- Verificação de URLs HTTP e HTTPS por meio do VirusTotal.
- Resultados confiável, suspeito, perigoso, em análise ou inconclusivo.
- Domínio, contagens de mecanismos, fonte e data do relatório quando disponíveis.
- Cache em memória, limite global de requisições e acompanhamento com consultas periódicas limitadas.
- Chat responsivo com horários, indicador de digitação e botão para limpar a conversa.
- Boas-vindas e uma dica sorteada de um catálogo de 30 dicas do backend a cada abertura ou recarga.
- Validação de entradas e rejeição de IPs privados, locais e reservados, nomes de rede local e URLs com credenciais.

## Configurar a chave do VirusTotal

Crie uma conta no VirusTotal Community, confirme o e-mail e, no menu do seu perfil, abra a seção **API key**. A chave é pessoal e deve ficar no ambiente do backend. [Guia oficial](https://docs.virustotal.com/reference/getting-started)

### PowerShell 7

Na pasta que contém o `pom.xml`, leia a chave sem exibi-la nem incluí-la no histórico do terminal e inicie a aplicação no mesmo terminal.

```powershell
$env:VIRUSTOTAL_API_KEY = Read-Host 'Chave da API do VirusTotal' -MaskInput
.\mvnw.cmd spring-boot:run
```

A variável vale para essa sessão do terminal. Encerre com `Ctrl+C`.

### Bash no Linux ou macOS

```bash
read -r -s -p 'Chave da API do VirusTotal: ' VIRUSTOTAL_API_KEY
export VIRUSTOTAL_API_KEY
sh ./mvnw spring-boot:run
```

O arquivo `.env.example` documenta as variáveis sem credenciais. O Spring Boot **não carrega `.env` automaticamente**. Configure as variáveis no terminal ou na configuração de execução da IDE.

### IntelliJ IDEA

1. Abra o `pom.xml` como projeto Maven e aguarde a importação das dependências.
2. Em **File → Project Structure → Project**, selecione um JDK 21.
3. Abra `src/main/java/com/linkguard/NettoApplication.java` e crie a configuração de execução pelo método `main`.
4. Em **Run → Edit Configurations**, selecione essa configuração e abra **Environment variables** em **Modify options**.
5. Adicione `VIRUSTOTAL_API_KEY` com o valor da sua chave, salve e execute `NettoApplication`.

Mantenha configurações com credenciais apenas na IDE local. Os diretórios `.idea`, `.vscode` e os arquivos `.env` estão ignorados no Git.

Se a variável estiver ausente, o servidor inicia e o chat informa que `VIRUSTOTAL_API_KEY` precisa ser configurada. Nesse caso o resultado é **INCONCLUSIVO**. Reinicie o servidor após alterar a chave.

## Executar localmente

Com JDK 21, use o Maven Wrapper incluído. Ele baixa o Maven e as dependências na primeira execução.

```powershell
.\mvnw.cmd spring-boot:run
```

Abra [http://localhost:8080](http://localhost:8080). O servidor escuta em `127.0.0.1` por padrão. Se o Maven estiver instalado, `mvn spring-boot:run` também funciona.

Para gerar e executar o JAR, use os comandos abaixo no Windows. No Linux ou macOS, substitua `.\mvnw.cmd` por `sh ./mvnw`.

```powershell
.\mvnw.cmd clean package
java -jar target/netto-bot-0.0.1-SNAPSHOT.jar
```

## Como a análise funciona

1. O backend valida o formato e normaliza o esquema e o domínio, preservando o caminho e os parâmetros da URL.
2. Se existir uma resposta recente no cache, ela é reutilizada.
3. O cliente consulta `GET /api/v3/urls/{id}` no VirusTotal com o identificador Base64 URL-safe sem preenchimento.
4. Relatórios com pareceres de mecanismos e data de análise dos últimos 24 horas são classificados. Na ausência de relatório utilizável ou com relatório antigo, o backend solicita `POST /api/v3/urls`.
5. A interface recebe **EM ANÁLISE** e consulta um identificador opaco do próprio Netto. O backend consulta `GET /api/v3/analyses/{id}` apenas quando o intervalo permite.
6. Ao concluir, o resultado substitui a mensagem em andamento. Falhas, limites ou dados insuficientes produzem **INCONCLUSIVO**.

O cliente só faz requisições para a API oficial do VirusTotal. Redirecionamentos HTTP estão desativados. O Netto não abre os sites enviados. A integração HTTP fica em `client/VirusTotalClient`, e a interpretação dos relatórios fica em `service/LinkClassificationService`. A lógica de análise pode ser chamada por uma futura interface de mensagens.

### Interpretação dos mecanismos

| Campo do VirusTotal | Exibição no chat | Uso na classificação |
| --- | --- | --- |
| `malicious` | Maliciosos | Qualquer detecção produz PERIGOSO |
| `suspicious` | Suspeitos | Produz SUSPEITO quando não há detecções maliciosas |
| `harmless` | Sem ameaças | Produz CONFIÁVEL quando há ao menos um parecer e zero detecções maliciosas ou suspeitas |
| `undetected` | Sem classificação | O mecanismo não emitiu parecer sobre a URL |

As contagens exibidas são as recebidas da API. Um relatório contendo apenas `undetected` é insuficiente para classificar como confiável. A data exibida vem de `last_analysis_date` ou `date`, conforme o endpoint consultado. [Objeto URL](https://docs.virustotal.com/reference/url-object), [objeto Analysis](https://docs.virustotal.com/reference/analyses-object)

### Cota e tempos

O padrão acompanha o plano Public API, com **4 requisições por minuto e 500 por dia**. O uso é sujeito às condições do VirusTotal, incluindo uso não comercial. Confira a cota disponível na sua conta e os termos antes de usar a chave. [Limites e condições oficiais](https://docs.virustotal.com/reference/public-vs-premium-api)

- Todas as consultas, submissões e verificações de andamento passam pelo mesmo limitador de uma janela móvel de 60 segundos. A contagem diária usa UTC.
- `VIRUSTOTAL_REQUESTS_PER_MINUTE` e `VIRUSTOTAL_REQUESTS_PER_DAY` permitem adequar os limites à sua conta. Aumente-os somente se o plano contratado permitir.
- O HTTP 429 interrompe o acompanhamento e aplica uma pausa global com pelo menos 60 segundos, respeitando `Retry-After` até 24 horas. A resposta ao usuário é inconclusiva.
- O intervalo de consulta é de pelo menos 20 segundos, com até 6 chamadas de andamento e prazo máximo de 5 minutos.
- Cada chamada HTTP tem timeout de conexão e de leitura de 10 segundos. A interface limita cada requisição local a 25 segundos.
- O cache mantém até 200 entradas. Resultados duram 15 minutos e falhas ficam por 1 minuto. Submissões simultâneas da mesma URL compartilham a mesma verificação.
- Cache e contadores são mantidos na memória de uma única instância. Reiniciar o processo os apaga. Consumo da mesma chave por outros programas permanece sujeito à cota real da conta, e um HTTP 429 continua sendo tratado.

Os tempos e o tamanho do cache podem ser ajustados pelas propriedades `virustotal.*` em `application.properties`. O intervalo de consulta tem mínimo de 20 segundos. As requisições são coordenadas por um bloqueio simples para manter a consistência desse protótipo.

## Privacidade e limites

**URLs enviadas são compartilhadas com o VirusTotal e podem passar a integrar seu conjunto de dados acessível à comunidade.** Não envie links privados, de recuperação de senha ou com tokens de acesso. Essa orientação também aparece permanentemente no chat. [Política de submissão da API](https://docs.virustotal.com/reference/scan-url)

O validador aceita mensagens de até 2.048 caracteres e bloqueia credenciais na URL, IPs locais, privados e reservados, formas numéricas ambíguas de IPv4 e nomes locais como `localhost`, `.local` e `.internal`. A validação do host é textual, sem resolver DNS nem acessar o destino. Um domínio público pode usar DNS interno ou mudar de endereço, e essa validação não inspeciona esses casos.

Os resultados representam os pareceres disponíveis na data indicada. Sites podem mudar, golpes recentes podem não ter sido detectados e mecanismos podem divergir. A classificação confiável significa que os pareceres disponíveis não sinalizaram ameaças. Mantenha cautela com dados pessoais, pagamentos e downloads.

As mensagens são renderizadas com `textContent`. Os endereços enviados permanecem como texto na conversa. A chave fica no backend, em variável de ambiente, e não é devolvida no JSON. A conversa fica apenas na memória da página e é apagada ao recarregar ou limpar o chat. Limpar o chat cancela o acompanhamento no navegador. Uma URL já compartilhada com o VirusTotal continua sujeita à política desse serviço.

## Testar a aplicação

```powershell
.\mvnw.cmd test
```

Os testes automatizados usam respostas de API simuladas **apenas nos testes** e não consomem cota real. Cobrem validação e rejeição de URLs privadas, as cinco situações de análise, contagens exatas, dados insuficientes, ausência de chave, falhas HTTP e de comunicação, limite por minuto e por dia, cache, concorrência, andamento limitado e endpoint de dicas.

Para validar com a API real, configure uma chave válida, inicie o servidor e envie uma URL pública sem dados sensíveis, como `https://example.com`. Confira a fonte, a data e as contagens recebidas. Um endereço sem relatório utilizável pode exigir uma submissão e alguns ciclos de acompanhamento. Os resultados dependem do relatório atual e da cota disponível.

**A implementação foi validada com testes automatizados e no navegador sem uma chave configurada. A consulta real ao VirusTotal com uma chave válida permanece pendente.**

## Endpoints

| Método e rota | Finalidade |
| --- | --- |
| `POST /api/chat` | Recebe JSON com o campo `message` e verifica a URL |
| `GET /api/chat/analyses/{id}` | Acompanha uma análise iniciada, usando o UUID devolvido pelo Netto |
| `GET /api/tips/random` | Retorna JSON com uma dica no campo `tip` |

O chat mantém `status` e `reply` como campos principais. Dados disponíveis acrescentam `domain`, `stats`, `analyzedAt` e `source`. Em andamento, a resposta contém `analysisId` e `retryAfterSeconds`. Os status são `CONFIÁVEL`, `SUSPEITO`, `PERIGOSO`, `EM_ANALISE` e `INCONCLUSIVO`. Entradas inválidas retornam HTTP 400 com uma orientação, enquanto resultados e indisponibilidade da análise retornam HTTP 200 com o respectivo status.

## Organização e próximas etapas

- `controller` expõe o chat e as dicas.
- `service` valida URLs, coordena as análises, classifica relatórios e sorteia dicas.
- `client` encapsula as chamadas do VirusTotal e o controle de cota.
- `dto`, `config` e `exception` definem os dados, a configuração e os erros tratados.
- `src/main/resources/static` contém a interface e a fonte local com sua licença.
- `src/test/java` contém os testes automatizados.

As próximas etapas podem incluir um webhook do WhatsApp Cloud API reutilizando `LinkAnalysisService`, outros provedores de reputação e melhorias de acompanhamento conforme o plano disponível. Essas etapas devem ser definidas antes de ampliar o projeto.
