# Spec — Manager como notetaker · Óculos só leitura

> Decisões do Marcus em 10/10/2026. Rascunho para validação e para o Beto (backend Base44).

## Princípios

1. **Os óculos só consomem informação.** Eles não tomam nenhuma ação. A única exceção são os gestos nativos de gravar e parar.
2. **O Manager é um notetaker** no estilo Fathom ou Wit AI: resumo, assuntos e botões de ação. Não é formulário.
3. **O que se sabe sobre a pessoa vira pílula.** A IA infere isso ao longo das conversas (por exemplo "amigo do bar", "conhecido da academia", "Rokid", "gosta de café"). O usuário pode corrigir, remover ou adicionar pílulas.
4. **Formulário só para valores absolutos:** aniversário, idade, onde trabalha e contatos. Esses campos servem para corrigir o que não foi captado na conversa ou foi salvo errado.
5. **Encontro sem foto não existe (nos óculos).** A pessoa é reconhecida pelo rosto, então todo encontro tem foto real do rosto, e nunca se usa um quadrado com iniciais no lugar. No Manager pode existir **pré-cadastro sem foto** (contatos importados de LinkedIn ou e-mail). Esses contatos não aparecem nos óculos até ganharem foto, por importação ou no primeiro encontro de verdade.
6. **Fonte única de verdade:** o HUD sempre mostra o último estado salvo no Manager.

## Óculos (HUD)

Display: **biocular**, ou seja, um único Micro-LED com guia de onda leva a mesma imagem aos dois olhos. Isso foi confirmado pelo Marcus em 10/10 e corrige o doc 02, que dizia "só olho direito". São 640×480 pixels, cerca de 30° de campo de visão na diagonal, centralizado. Na prática, o display ocupa mais ou menos o terço central da visão.

Card da pessoa, só leitura, com 70% da largura do display, pra não tampar a visão nem chamar atenção de quem está na frente. O indicador "Gravando encontro" fica fora do card, numa linha própria acima dele.

**Dois modos (decidido em 10/10).** Numa feira, o app fica ligado e grava os encontros sozinho, sem você mexer nos óculos; o histórico fica pra quando você estiver sozinho, num café. Durante a conversa não se lê; se bate o olho. Ler e expandir fica pra fora da conversa.

1. **Bater o olho (durante a conversa):**
   - Foto quadrada média, NOME em caixa alta e as pílulas de onde você conhece a pessoa (TRABALHO, AMIGO, ACADEMIA).
   - Uma frase só (`headline`), por exemplo "Colega de trabalho há 2 anos".
   - Sem dossiê, sem linha do tempo, nada pra expandir. A ideia é não parecer distraído na frente da pessoa.
2. **Revisão (fora da conversa):**
   - Quando o app está em repouso (ninguém na frente, nada gravando), aparece só uma linha discreta embaixo: **ÚLTIMOS REGISTROS ⌃**.
   - O toque abre uma coluna estreita no canto (cerca de 40% da largura) com cards curtos: foto pequena ao lado do nome curto ("Marcus M.") e da data. Ocupa pouco espaço de propósito: se alguém aparecer ou você precisar pagar algo, nada atrapalha, e 2 toques fecham.
   - Entram **todos os registros, não só pessoas**: pessoas, moto, chave e o que mais as skills registrarem. Cada item tem foto, título e uma linha de contexto ("MARCUS M. · ontem 18:20", "MOTO · R. Augusta · 300 m", "CHAVE · mesa da sala · 22:10"). O VisualContext dá contexto às coisas, não só aos encontros. A lista vai do mais recente pro mais antigo, com scroll vertical infinito, carregando de 12 em 12. Depois isso vira comando de voz ("mostre meus últimos encontros").
   - Deslizar ‹ › escolhe; toque abre o card completo.
   - O card completo tem foto, nome e pílulas, **SOBRE** (dossiê de 3–4 linhas) e **ENCONTROS** (mini-cards de 2 linhas com chevron: toque expande, 2 toques recolhem).
   - 2 toques voltam um nível: encontro expandido → card → lista → fecha.
   - Se alguém aparecer na frente, a revisão fecha: a conversa ao vivo tem prioridade.

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
| **"Registra isso"** (qualquer coisa) | papel, receita, cardápio, documento, placa: o que você estiver olhando quando falar | foto, título dado pela IA ("RECEITA · Dr. Silva") e hora; texto completo lido por OCR |

- **Pergunta por voz:** "Onde deixei minha moto?" faz o HUD mostrar o card do veículo.
- **Navegação delegada:** o VisualContext não navega. "Mostrar no mapa" entrega o destino pra navegação nativa ou pro Rokid-GMaps do Anezium, conforme o princípio de registro, não executor.
- **Não é standalone (decidido em 10/10).** Os óculos precisam estar conectados ao celular por Bluetooth, como a maioria dos apps. É do celular que vêm o GPS e o processamento pesado.
- **A foto do objeto é guardada.** Isso faz parte do cadastro do objeto: guarda-se o recorte do objeto, e só o último.

### Skills criadas por frase natural (sem prompt)

O usuário só fala, por exemplo: *"Essa é a minha moto, lembra dela."* A IA entende sozinha a intenção ("lembrar onde foi visto por último") e cria a skill: a assinatura visual do objeto e o que guardar (frame, GPS, descrição do lugar). No app do celular, uma página de configurações lista as skills aprendidas, com frases de exemplo. A chave e a moto já vêm prontas no MVP.

### "Registra isso" (10/10)

- Você olha pra algo e fala **"Registra isso"**. Os óculos tiram a foto, leem o texto (OCR) e a IA dá um título e um resumo curto.
- O resultado vira um registro na lista ÚLTIMOS REGISTROS, nos óculos e no emulador.
- **No Manager:** foto e texto completo, mais as ações de enviar pro destino que o usuário escolher nos conectores:

| Destino | Situação (até onde sabemos) |
|---|---|
| Google Tasks | API aberta: funciona |
| Notion / Google Docs | funcionam |
| Google Keep | API só pra contas Google Workspace (empresa), não pra Gmail pessoal |
| Notas do Mac/iPhone | sem API pública; caminho via Atalho (Shortcuts) que recebe o texto |

- Receitas e documentos podem ter dados de saúde e dados pessoais. Esses registros só devem ser guardados depois da correção de segurança do backend.

### Skill "Registra isso" (registro livre, 10/10)

Você olha pra qualquer coisa (um texto, uma folha impressa, uma receita médica, uma placa) e fala **"Registra isso"**. Os óculos fazem o seguinte:

- capturam o frame e leem o texto (OCR);
- a IA dá um título curto ("Receita · Dr. Silva", "Wi-Fi do hotel") e um resumo;
- o resultado vira um **registro**.

Onde aparece:
- **Óculos e emulador:** na lista "Últimos registros", com foto, título e contexto.
- **Manager:** o registro completo, com foto, texto lido e resumo, e botões de **sincronizar com** o conector que o usuário escolher.

Destinos que o usuário pode escolher:
- Google Tasks: tem API.
- Google Keep: até onde sei, a API oficial é restrita a contas de empresa (Workspace); pra contas pessoais talvez não dê. **Verificar.**
- Notas do Mac / Apple Notes: não tem API na nuvem; provavelmente só via Atalhos (Shortcuts) no próprio aparelho. **Verificar.**
- Notion e Google Docs: têm API.

Atenção: receita médica é dado de saúde, que a LGPD trata como dado sensível. Esses registros devem ficar privados por padrão e nunca ser sincronizados sem o usuário pedir.

### Como reconhecer o que é seu (proposta)

- **Na mão = é seu.** Se a chave aparece na sua mão, ela é sua. Nesse momento a IA guarda várias vistas da chave com o chaveiro, que é o que a torna única, e monta uma assinatura visual do objeto. Depois disso, não é qualquer chave: só a que bate com essa assinatura.
- **Registrar o momento que importa, não vigiar o tempo todo.** O "visto por último" que interessa é quando o objeto **sai da sua mão** (mão com chave → mão vazia). Só aí se guarda o frame, a descrição do lugar ("mesa da sala, ao lado do notebook") e a hora. Isso gasta muito menos bateria e processamento.
- **Veículo, com ajuda do celular.** O Android já detecta quando você para de estar num veículo e começa a andar (é assim que o Google Maps sabe onde você estacionou). Essa troca, somada ao frame dos óculos vendo a moto, marca onde ela ficou, com GPS. Isso funciona mesmo se os óculos não "virem" a moto naquele instante.
- **Dentro de casa o GPS não ajuda.** Pra chave vale a descrição da cena, não as coordenadas.
- **Opcional, no futuro:** se a pessoa tiver uma tag Bluetooth no chaveiro (por exemplo SmartTag), ela serve de reforço. Nunca é obrigatória.

## Fotos dos contatos importados (pré-cadastro)

Ter foto antes do primeiro encontro pré-alimenta o reconhecimento, e é muito importante. Fontes:

| Fonte | Dá foto? | Observação |
|---|---|---|
| Agenda do celular (app companion) | sim, quando o contato tem foto | lê as fotos dos contatos do próprio telefone, com permissão |
| Google Contatos (integração Google no Base44) | sim, quando o contato tem foto | **Beto** |
| LinkedIn: exportação oficial | **não** | traz nome, cargo, empresa, e-mail e data, sem foto |
| LinkedIn / Facebook: API | **não** | não liberam foto da rede pra apps de terceiros |
| LinkedIn: raspagem automática | não fazer | viola os termos e arrisca bloquear a conta |
| **Print do perfil → Manager** | sim | o Manager acha o rosto no print, recorta e associa ao contato (spec C: pré-cadastro com foto externa) |

Atenção: a foto vira assinatura facial, ou seja, dado biométrico de terceiros, que a LGPD trata como dado sensível. Pra uso pessoal está ok; antes de virar produto, é preciso definir uma regra de consentimento.

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
- Campos da pessoa que o HUD lê:
  - `headline`: 1 frase curta pro modo bater o olho, até ~40 caracteres.
  - `pills`: até 3, por exemplo trabalho, amigo, academia.
  - `dossier`: 3–4 linhas, até ~45 palavras, pra revisão.
  - Todos são reescritos a cada encontro.
- Onde guardar ações e pílulas, e o estado de cada ação (sugerida, aceita, descartada).
- Onde guardar os **registros de objetos** (moto, chave): foto do recorte, título, lugar ou descrição, GPS e hora. A lista "Últimos registros" lê pessoas e objetos no mesmo formato.
- Conectores: Google Agenda e Google Tasks.

**Claude**
- Telas do Manager (PT/EN): página do encontro, página da pessoa com pílulas e o formulário curto.
- O HUD passa a ler pílulas e campos do Manager.
- O emulador chama `analyze_encounter` ao final de cada encontro.

## Em aberto

1. ~~A linha do tempo fica no card dos óculos?~~ Decidido em 10/10: sim, abaixo do dossiê geral.
2. As ações automáticas do modo Assistência total devem ser confirmadas depois no Manager, ou valem direto?
