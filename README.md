# Sistema multiagente multicast para risco cardiológico

Projeto didático em **Java SE 17+**, sem bibliotecas externas. Cinco aplicações com `main` independente trocam exclusivamente datagramas pelo grupo multicast. As escalas são as do enunciado e **não servem para avaliar pessoas reais**.

## Começar no Windows / PowerShell

Na pasta do projeto, com um JDK 17 ou superior no PATH:

```powershell
./build.ps1
java -cp build/classes br.edu.cardio.Coordenador --listar-interfaces
```

Escolha uma interface **ativa, com IPv4 e multicast**, conectada à rede do laboratório. Use o nome listado pelo Java ou seu IP local. O nome exibido pelo Windows pode ser diferente. Não copie o IP de outra máquina.

Abra cinco terminais. Nos comandos seguintes, substitua `IP_LOCAL` pelo IP da máquina que executa aquele processo e `7` pelo número real do grupo:

```powershell
java -cp build/classes br.edu.cardio.Tabagismo --interface IP_LOCAL --grupo 7
java -cp build/classes br.edu.cardio.Sedentarismo --interface IP_LOCAL --grupo 7
java -cp build/classes br.edu.cardio.Pressao --interface IP_LOCAL --grupo 7
java -cp build/classes br.edu.cardio.Imc --interface IP_LOCAL --grupo 7
java -cp build/classes br.edu.cardio.Coordenador --interface IP_LOCAL --grupo 7
```

Cada comando roda em seu próprio terminal. No coordenador, digite `novo` para responder às perguntas, `caso 3` para executar um exemplo ou `sair` para encerrar. Todos os agentes aceitam `sair`. EOF também encerra. Os processos deixam o grupo e fecham o socket. Durante a coleta de uma avaliação o coordenador aguarda até o prazo configurado antes de ler o próximo comando. Em uma pergunta, `sair` cancela a entrevista e encerra.

Atalho equivalente: `./run.ps1 Imc --interface IP_LOCAL --grupo 7`. Os comandos `java` acima são portáveis entre sistemas.

## Executar exemplos sem entrevista

Com os quatro agentes já iniciados:

```powershell
java -cp build/classes br.edu.cardio.Coordenador --interface IP_LOCAL --grupo 7 --casos 1,2,3,4,5,6
java -cp build/classes br.edu.cardio.Coordenador --interface IP_LOCAL --grupo 7 --dados config/paciente-exemplo.properties
java -cp build/classes br.edu.cardio.Coordenador --interface IP_LOCAL --grupo 7 --dados config/paciente-invalido.properties
```

Os modos `--caso`, `--casos` e `--dados` encerram após executar. São mutuamente exclusivos. Uma avaliação incompleta é um resultado tratado e aparece como `INCOMPLETA`; não implica erro de inicialização nem código de saída diferente de zero. Arquivos inválidos ou falha de configuração encerram com erro.

O coordenador coleta e encaminha valores brutos. Só o agente correspondente pontua e valida. Os questionários usam **pontos da alternativa**, não posição da alternativa: em T1, fumante é `4`, e `3` é inválido. Reais usam ponto: `1.75`.

## Parâmetros (todos os processos)

| Opção | Padrão | Efeito |
|---|---|---|
| `--interface nome-ou-IP` | obrigatória | Interface explícita para ingresso e envio |
| `--grupo G` | 7, ilustrativo | G em 1..254; deriva `239.0.0.G` e `5000+G` |
| `--endereco IPv4` | derivado de G | Sobrescreve o endereço multicast |
| `--porta N` | derivada de G | Sobrescreve a porta, 1..65535 |
| `--timeout-ms N` | 3000 | Prazo total de uma avaliação, desde antes do envio; 1..600000 |
| `--threads N` | 4 | Trabalhadores de cada agente; 1..64 |
| `--delay-ms N` | 0 | Atraso artificial do agente para testes; 0..60000 |
| `--logs pasta` | logs | Pasta de registros UTF-8, um arquivo por processo |
| `--config caminho` | nenhum | Propriedades de configuração, sobrescritas pela CLI |
| `--listar-interfaces` | ação | Exibe interfaces e encerra, sem ingressar no grupo |
| `--ajuda` | ação | Exibe ajuda e encerra |

Somente no coordenador: `--caso 1..6`, `--casos 1,2,...`, `--dados arquivo.properties`. `--threads` e `--delay-ms` só afetam agentes; `--timeout-ms` só afeta coordenadores.

Exemplo de arquivo: edite `config/laboratorio.properties` em cada máquina e execute:

```powershell
java -cp build/classes br.edu.cardio.Imc --config config/laboratorio.properties
```

O TTL é sempre 1, por requisito. Não existe parâmetro para aumentá-lo. O projeto não altera firewall nem rede. As máquinas precisam estar em uma rede que permita o grupo e a porta UDP escolhidos. Se houver bloqueio, siga a orientação do professor sobre a alternativa de máquina única.

## Compilar em Linux / macOS

Na raiz, usando JDK 17+ e shell POSIX:

```sh
mkdir -p build/classes
find src test -name '*.java' > build/sources.txt
javac --release 17 -encoding UTF-8 -Xlint:all -d build/classes @build/sources.txt
java -cp build/classes br.edu.cardio.UnitTests
```

Os cinco comandos de execução são os mesmos, com a interface local apropriada.

## Protocolo e precisão

UTF-8, exatamente cinco campos, com até 4096 bytes por datagrama:

```text
TIPO;UUID_AVALIACAO;ORIGEM;DESTINO;DADOS
REQ;uuid;COORD-uuid-instancia;TABAGISMO;4,2,2,0,0
RESP;uuid;TABAGISMO;COORD-uuid-instancia;0.6153846153846154
ERRO;uuid;IMC;COORD-uuid-instancia;altura deve ser finita e positiva
```

Os exemplos usam `uuid` como abreviação. Na execução são UUIDs completos, gerados com `UUID.randomUUID()`. Toda resposta vai para o **mesmo endpoint multicast**, e seu campo DESTINO identifica o coordenador lógico. Nenhum endereço individual de coordenador é usado no envio. Não há TCP, RMI ou UDP unicast entre processos.

São descartadas mensagens próprias, de outro destino, tipo incompatível, origem desconhecida, avaliação desconhecida, duplicadas ou tardias. Todas são registradas. Os últimos 256 identificadores de cada coordenador são mantidos para identificar duplicatas depois da consolidação. Após a expulsão desse histórico, a resposta é descartada como avaliação desconhecida.

No fio usa-se `Double.toString`, que preserva o `double` e independe do idioma. Os graus exibidos usam `Locale.US` e três casas decimais. **Não se arredondam os graus enviados nem os cálculos intermediários**, para conservar a precisão solicitada pelo enunciado. A precisão do formato sugerido de três casas foi ampliada deliberadamente para evitar acúmulo de erro.

## Escalas e consolidação

* Tabagismo: soma dos cinco pontos / 13. Sedentarismo: soma / 9.
* PA: `max(clamp((PAS-120)/60), clamp((PAD-80)/30))`.
* IMC: `clamp((peso/(altura*altura)-25)/15)`.
* `clamp(x) = max(0,min(1,x))`.
* Altura aceita: **0,50 a 2,50 m**, inclusive; faixa didática definida pelo projeto. Pressões e peso devem ser finitos e positivos.
* Cada NAP: `mu=a`, `lambda=1-b`, `Gc=mu-lambda`, `Gct=mu+lambda-1`, `muE=(Gc+1)/2`.
* NAP1 recebe T/S; NAP2 recebe PA/IMC; NAP3 recebe os resultados dos anteriores.
* Condições em ordem: `Gc>=0.5` V; `Gc<=-0.5` F; `Gct>=0.5` T; `Gct<=-0.5` paracompleto; restante não extremo.

As fórmulas ausentes nas imagens do texto colado foram reconstruídas a partir das interpolações descritas e do exemplo resolvido. São consistentes com os seis resultados esperados. A escala de IMC ignora o baixo peso; as classificações seguem o exercício, não uma validação clínica.

## Testar

```powershell
./test.ps1
./test.ps1 -Interface IP_LOCAL
```

O segundo comando compila, executa os testes unitários e inicia JVMs reais para testar multicast **na mesma máquina**. Usa grupo 247 por padrão para evitar interferência com a demonstração. Para trocar: `-Grupo 246`. Os agentes do teste são encerrados pelo próprio teste. Não execute duas suítes no mesmo grupo/porta ao mesmo tempo.

Também pode executar diretamente:

```powershell
java -cp build/classes br.edu.cardio.UnitTests
java -cp build/classes br.edu.cardio.IntegrationTests --interface IP_LOCAL --grupo 247
```

São verificados: seis casos, erro de altura, ausência por 3 s, reingresso, coordenadores simultâneos, duplicata, pool concorrente e saída limpa. Há 210 verificações unitárias para escalas, limites, valores não finitos, protocolo, média, prioridade das classificações, erros, duplicidade e respostas tardias.

Os resultados da execução realizada estão em `evidencias/local/`; `ultimo-teste.txt` aponta para a pasta dos logs completos. As linhas de console são capturas textuais reais, **não capturas de tela**. O relatório contém excertos desses registros e explica a limitação do ambiente.

## Demonstração e entrega

Distribuição proposta: máquina A, coordenador; B, tabagismo e sedentarismo em JVMs distintas; C, pressão e IMC em JVMs distintas. Em cada máquina, informe sua própria interface; o grupo e a porta são iguais em todas. Nada precisa ser recompilado para mudar a distribuição.

Siga `docs/ROTEIRO-LABORATORIO.md`. Ainda é necessário preencher nomes, grupo real, máquinas efetivamente usadas, divisão real de tarefas, logs distribuídos e capturas de tela. Isso não foi inventado.

O PDF está em `output/pdf/relatorio-arquitetura.pdf`; a fonte editável é `docs/relatorio.md`, e `tools/gerar_relatorio.py` a renderiza com dados e trechos reais do projeto. Python/ReportLab são necessários apenas para regenerar o PDF, não para compilar, testar nem executar a aplicação Java.

Estrutura principal:

```text
src/br/edu/cardio/    aplicações e componentes Java SE
test/br/edu/cardio/   testes sem dependências
config/              exemplos de configuração e entradas
docs/                relatório editável e roteiro
evidencias/local/    resultados e registros da execução real
output/pdf/          relatório em PDF
tools/               gerador do relatório
```

Limitações intencionais: não há confirmação de entrega, retransmissão, eleição de coordenador, descoberta de agentes, autenticação ou criptografia. Timeout detecta ausência de resposta, mas não distingue agente parado de perda de datagrama. Uma resposta falsificada com identificadores conhecidos pode ser aceita. Cada agente usa um pool com fila limitada a 128 tarefas e responde com erro de sobrecarga se a fila estiver cheia. O projeto opera no modelo didático de rede confiável do laboratório.
