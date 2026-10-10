# Spec — Manager como notetaker · Óculos só leitura

> Decisões do Marcus em 10/10/2026. Rascunho para validação e para o Beto (backend Base44).

## Princípios

1. **Os óculos só consomem informação.** Eles não tomam nenhuma ação. A única exceção são os gestos nativos de gravar e parar.
2. **O Manager é um notetaker** no estilo Fathom ou Wit AI: resumo, assuntos e botões de ação. Não é formulário.
3. **O que se sabe sobre a pessoa vira pílula.** A IA infere isso ao longo das conversas (por exemplo "amigo do bar", "conhecido da academia", "Rokid", "gosta de café"). O usuário pode corrigir, remover ou adicionar pílulas.
4. **Formulário só para valores absolutos:** aniversário, idade, onde trabalha e contatos. Esses campos servem para corrigir o que não foi captado na conversa ou foi salvo errado.
5. **Fonte única de verdade:** o HUD sempre mostra o último estado salvo no Manager.

## Óculos (HUD)

Card da pessoa, só leitura, ocupando a largura do display (640×480):

1. **Topo:** foto, nome e uma definição curta: social ("amigo da academia") e/ou de trabalho ("CEO · Empresa"). Sem e-mail, a não ser que o usuário pergunte.
2. **Dossiê geral** com 3–4 linhas, escrito pela IA a partir de todos os encontros. Exemplos: "Amigo seu, registrado 4 vezes no último ano…" ou "CEO da empresa tal. Vocês conversaram na feira XYZ; ele é fornecedor da empresa dos seus sócios." Enquanto o dossiê da IA não existe, o emulador monta um texto simples com o que já está salvo.
3. **Linha do tempo:** cada encontro é um mini-card de 2 linhas (dia, hora e lugar; resumo em 1 linha) com um chevron no canto direito.
   - Deslizar ‹ › move o destaque, que aparece como contorno no mini-card.
   - Toque expande, 2 toques recolhem.

Gravação de encontro: já está implementada no emulador.
- O buffer começa no contato visual com fala.
- Pessoa nova: contagem "Gravando encontro: 5…0".
- Durante a gravação: círculo discreto no canto.
- 2 toques param a gravação. Toque retoma.

Visual: monocromático (verde e transparência sobre o preto), sem dicas de gesto e sem redundância.

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
