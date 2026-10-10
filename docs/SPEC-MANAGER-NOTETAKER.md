# Spec — Manager como notetaker · Óculos só leitura

> Decisões do Marcus em 10/10/2026. Rascunho para validação e para o Beto (backend Base44).

## Princípios

1. **Os óculos só consomem informação.** Eles não tomam nenhuma ação. A única exceção são os gestos nativos de gravar e parar.
2. **O Manager é um notetaker** no estilo Fathom ou Wit AI: resumo, assuntos e botões de ação. Não é formulário.
3. **O que se sabe sobre a pessoa vira pílula.** A IA infere isso ao longo das conversas (por exemplo "amigo do bar", "conhecido da academia", "Rokid", "gosta de café"). O usuário pode corrigir, remover ou adicionar pílulas.
4. **Formulário só para valores absolutos:** aniversário, idade, onde trabalha e contatos. Esses campos servem para corrigir o que não foi captado na conversa ou foi salvo errado.
5. **Fonte única de verdade:** o HUD sempre mostra o último estado salvo no Manager.

## Óculos (HUD)

Display: **biocular**, ou seja, um único Micro-LED com guia de onda leva a mesma imagem aos dois olhos. Isso foi confirmado pelo Marcus em 10/10 e corrige o doc 02, que dizia "só olho direito". São 640×480 pixels, cerca de 30° de campo de visão na diagonal, centralizado. Na prática, o display ocupa mais ou menos o terço central da visão.

Card da pessoa, só leitura, com 70% da largura do display (640×480), pra não tampar a visão nem chamar atenção de quem está na frente. O indicador "Gravando encontro" fica fora do card, numa linha própria acima dele.

1. **Topo:** foto, nome e uma definição curta: social ("amigo da academia") e/ou de trabalho ("CEO · Empresa"). Sem e-mail, a não ser que o usuário pergunte.
2. **Dossiê geral** com 3–4 linhas, escrito pela IA a partir de todos os encontros. Exemplos: "Amigo seu, registrado 4 vezes no último ano…" ou "CEO da empresa tal. Vocês conversaram na feira XYZ; ele é fornecedor da empresa dos seus sócios." Enquanto o dossiê da IA não existe, o emulador monta um texto simples com o que já está salvo.
3. **Linha do tempo:** cada encontro é um mini-card de 2 linhas (dia, hora e lugar; resumo em 1 linha) com um chevron no canto direito.
   - Deslizar ‹ › move o destaque, que aparece como contorno no mini-card.
   - Toque expande, 2 toques recolhem.

Gravação de encontro: já está implementada no emulador.
- O buffer começa no contato visual com fala.
- Pessoa nova: contagem "Gravando encontro: 5…0".
- Durante a gravação: indicador discreto acima do card.
- 2 toques param a gravação. Toque retoma.

Visual: monocromático (verde e transparência sobre o preto), sem dicas de gesto e sem redundância.

## Skills do MVP: rosto, chave e veículo (10/10)

O VisualContext não é só sobre pessoas. O MVP terá três skills de memória, e cada uma tem o seu card no HUD, sem coluna de ícones fixa na tela:

| Skill | O que registra | Card no HUD |
|---|---|---|
| **Rosto** | encontros com pessoas | identidade, dossiê e linha do tempo (acima) |
| **Chave** (de casa) | onde a chave foi vista por último | foto do último frame em que apareceu, descrição ("em cima da mesa da sala") e quando |
| **Veículo** (moto, carro) | onde ficou estacionado | foto do último frame, rua, distância e quando, mais a ação **mostrar no mapa / navegar** |

- **Pergunta por voz:** "Onde deixei minha moto?" faz o HUD mostrar o card do veículo.
- **Navegação delegada:** o VisualContext não navega. "Mostrar no mapa" entrega o destino pra navegação nativa ou pro Rokid-GMaps do Anezium, conforme o princípio de registro, não executor.
- **Localização vem do celular ou do relógio,** porque os óculos não têm GPS próprio.

Decisões em aberto pra essas skills:
1. **Foto do objeto:** a spec de transcrição dizia que a única imagem guardada era o recorte do rosto. Pra chave e veículo, guardar o último frame é uma exceção. A proposta é guardar só o recorte do objeto (sem pessoas e com placas de terceiros borradas), e só o último, sobrescrevendo o anterior.
2. **Como a pessoa ensina o que é dela** ("esta é a minha moto"): por uma captura nos óculos ou por foto no Manager.
3. **Quem atende a pergunta por voz:** o próprio VisualContext ou o assistente Nexus, que repassa pro VisualContext. Isso tem a ver com o formato de integração entre plugins do Rokid-Nexus, ainda a verificar.

## Manager · página do encontro

| Bloco | Conteúdo |
|---|---|
| Resumo | 2–3 linhas |
| Principais assuntos | tópicos curtos |
| Pendências / follow-ups | quem ficou de fazer o quê |
| Ações sugeridas | botões **Adicionar ao calendário** e **Criar tarefa** (depois: rascunho de e-mail). Cada uma: aceitar · editar · descartar |
| Transcrição | recolhida, com horário por fala |

Os dados de contato lidos por OCR (crachá ou cartão de visita) viram uma ação sugerida do tipo "adicionar e-mail ao contato".

## Manager · pessoa

- **Cabeçalho:** foto e nome.
- **Pílulas da IA:** relação, empresa, cargo, interesses, assuntos recorrentes. Tocar numa pílula edita ou remove; o "+" adiciona uma nova.
- **Dados fixos** (formulário curto): aniversário, idade, onde trabalha, e-mail, telefone.
- **Encontros:** lista que leva à página de cada encontro.

## Modos de uso (fase 2)

- **Assistência total:** grava tudo, e tarefas e eventos podem ser criados automaticamente pelo backend, nunca pelos óculos.
- **Executivo / business:** nada é automático. Depois do encontro, a pessoa revisa e decide no Manager, no celular ou no laptop.

## Divisão de trabalho

**Beto (Base44)**
- Pré-requisito: corrigir a segurança do backend. Hoje qualquer pessoa lê, altera e apaga contatos, e isso precisa estar resolvido antes de guardar transcrições.
- Ação `analyze_encounter`, que recebe a transcrição e devolve:
  `{ summary, topics[], follow_ups[], actions[{ type: calendar|task|contact, title, when?, confidence }], person_pills[{ label, evidence }], person_facts[{ key, value, evidence }] }`
- Campo `dossier` da pessoa: texto de 3–4 linhas, reescrito a cada encontro (o HUD lê esse campo).
- Onde guardar ações e pílulas, e o estado de cada ação (sugerida, aceita, descartada).
- Conectores: Google Agenda e Google Tasks.

**Claude**
- Telas do Manager (PT/EN): página do encontro, página da pessoa com pílulas e o formulário curto.
- O HUD passa a ler pílulas e campos do Manager.
- O emulador chama `analyze_encounter` ao final de cada encontro.

## Em aberto

1. ~~A linha do tempo fica no card dos óculos?~~ Decidido em 10/10: sim, abaixo do dossiê geral.
2. As ações automáticas do modo Assistência total devem ser confirmadas depois no Manager, ou valem direto?
