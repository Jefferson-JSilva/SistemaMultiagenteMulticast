# Roteiro da demonstração e coleta de evidências

## Identificação a preencher pelo grupo

* Número real do grupo: __________________
* Integrantes e matrículas: __________________
* Professor, turma e data da demonstração: __________________
* Máquina A / interface / IP / SO / JDK: __________________
* Máquina B / interface / IP / SO / JDK: __________________
* Máquina C / interface / IP / SO / JDK: __________________

## Preparação

1. Copiar o projeto para três máquinas e compilar com JDK 17+.
2. Escolher o grupo `239.0.0.G` e a porta `5000+G`, iguais nos processos.
3. Descobrir as interfaces com `--listar-interfaces` em cada máquina.
4. Na B, iniciar Tabagismo e Sedentarismo em terminais separados. Na C, Pressao e Imc. Na A, Coordenador.
5. Registrar a distribuição real. Se multicast entre máquinas estiver bloqueado, avisar o professor e documentar a autorização para uma máquina.

## Cenários obrigatórios

| Etapa | Procedimento | Evidência e resultado esperado |
|---|---|---|
| Normal | Coordenador com `--casos 1,2,3,4,5,6` | Quatro graus, três NAPs, estados F/V/não extremo/T/paracompleto/não extremo |
| Falha | `sair` no IMC, depois `caso 3` no coordenador | Cerca de 3 s, `AUSENTE IMC`, `INCOMPLETA`, nenhuma consolidação |
| Reingresso | Reiniciar apenas IMC, repetir caso 3 | Gc=0.115; Gct=0.279; tendência a elevado |
| Simultâneas | Dois coordenadores interativos; disparar `caso 1` e `caso 2` juntos | UUIDs distintos; F e V nos coordenadores correspondentes |
| Duplicado | Segunda instância Tabagismo, repetir caso 3 | `DESCARTADA_DUPLICADA` no coordenador; só uma consolidação |
| Entrada inválida | Coordenador com `--dados config/paciente-invalido.properties` ou altura 0 na entrevista | `ERRO` do IMC e `INCOMPLETA` |
| Encerramento | `sair` em todos os terminais | `LEAVE` antes de `CLOSE` nos logs |

No teste de duplicata, use coordenador interativo e aguarde o segundo datagrama antes de sair. Se necessário, a segunda instância pode usar `--delay-ms 150` para demonstrar a resposta chegando depois da consolidação.

## Guardar e anexar ao relatório

* Copiar logs de todas as máquinas para `evidencias/laboratorio/maquina-A`, `maquina-B` e `maquina-C`.
* Fazer capturas de tela reais por etapa, com processos, comandos e resultados legíveis. Nomear `01-normal.png`, `02-falha.png`, etc. Não apresentar imagem de um log renderizado como captura de terminal.
* Preencher tabela esperado/obtido para os seis casos e observações de rede, timeout e duplicata.
* Explicar qualquer diferença; tolerância numérica de 0,001. Não arredondar durante os cálculos.
* Atualizar o relatório editável com resultados distribuídos, imagens e divisão de tarefas efetivamente realizada.

## Divisão real das tarefas

| Integrante | Implementação/revisão realizada | Testes e documentação realizados |
|---|---|---|
| A preencher | A preencher | A preencher |
| A preencher | A preencher | A preencher |
| A preencher | A preencher | A preencher |
| A preencher | A preencher | A preencher |

Todos devem conseguir operar e explicar qualquer processo, independentemente da divisão acima.
