# Spec — Manager como notetaker · Óculos só leitura

> Decisões do Marcus em 10/10/2026. Rascunho para validação e para o Beto (backend Base44).

## Princípios

1. **Os óculos só consomem informação.** Eles não tomam nenhuma ação. A única exceção são os gestos nativos de gravar e parar.
2. **O Manager é um notetaker** no estilo Fathom ou Wit AI: resumo, assuntos e botões de ação. Não é formulário.
3. **O que se sabe sobre a pessoa vira pílula.** A IA infere isso ao longo das conversas (por exemplo "amigo do bar", "conhecido da academia", "Rokid", "gosta de café"). O usuário pode corrigir, remover ou adicionar pílulas.
4. **Formulário só para valores absolutos:** aniversário, idade, onde trabalha e contatos. Esses campos servem para corrigir o que não foi captado na conversa ou foi salvo errado.
5. **Encontro sem foto não existe (nos óculos).** A pessoa é reconhecida pelo rosto, então todo encontro tem foto real do rosto, e nunca se usa um quadrado com iniciais no lugar. No Manager pode existir **pré-cadastro sem foto** (contatos importados de LinkedIn ou e-mail). Esses contatos não aparecem nos óculos até ganharem foto, por importação ou no primeiro encontro de verdade.
6. **Tudo é privado e pessoal.** É uma extensão da sua memória: nada é compartilhado com outra pessoa, e ninguém mais consegue usar os seus óculos pra acessar as suas memórias (ver **Privacidade e bloqueio**).
7. **Fonte única de verdade:** o HUD sempre mostra o último estado salvo no Manager.

## Óculos (HUD)

Display: **biocular**, ou seja, um único Micro-LED com guia de onda leva a mesma imagem aos dois olhos. Isso foi confirmado pelo Marcus em 10/10 e corrige o doc 02, que dizia "só olho direito". São 640×480 pixels, cerca de 30° de campo de visão na diagonal, centralizado. Na prática, o display ocupa mais ou menos o terço central da visão.

Card da pessoa, só leitura, com 70% da largura do display, pra não tampar a visão nem chamar atenção de quem está na frente. Indicador de gravação: **um só**, a linha de status no canto inferior direito ("gravando"), em **verde 100%**. Nada de pílula em cima (era redundante). Todos os contornos do HUD com a **mesma espessura, 2 px**; nunca linha fina.

**Início e pausa (10/10):** **a gravação começa desde o primeiro instante.** Com a câmera ligada, o app ouve o tempo todo e guarda os últimos ~30 s só na memória (pré-gravação). Quando um encontro se abre, esse trecho entra no começo da transcrição. Se não vira encontro, é descartado. O começo da conversa nunca se perde. Os ~7 s de contato visual definem só quando a **mensagem** aparece (a contagem de pessoa nova), não quando a gravação começa. **5 s de silêncio pausam a gravação** para economizar bateria. O encontro continua aberto e a gravação volta sozinha quando alguém fala de novo. No app real, quem detecta a volta da fala é um detector de voz leve, rodando com o microfone em baixo consumo. No modo "Só o olhar basta", o silêncio não pausa.

**Gatilho da gravação (ajuste):** padrão = contato visual **e** fala. Nas configurações do app do celular dá para trocar para **"Só o olhar basta"**: grava mesmo sem fala (ex.: visitar alguém que quase não fala, como a mãe com Alzheimer, onde o encontro é olhar, gesto e carinho).

**Zona de visão (regra fixa):** toda informação relevante fica nos **70% de cima da altura** do display. A faixa de baixo fica embaçada, é difícil de alcançar com o olhar e exige olhar para baixo, o que quebra o "estou olhando pra você". Nada importante abaixo dessa linha.

**Dois modos (decidido em 10/10).** Numa feira, o app fica ligado e grava os encontros sozinho, sem você mexer nos óculos; o histórico fica pra quando você estiver sozinho, num café. Durante a conversa não se lê; se bate o olho. Ler e expandir fica pra fora da conversa.

1. **Bater o olho (durante a conversa):**
   - Foto quadrada média, NOME em caixa alta e as pílulas de onde você conhece a pessoa (TRABALHO, AMIGO, ACADEMIA).
   - Uma frase só (`headline`), por exemplo "Colega de trabalho há 2 anos".
   - Sem dossiê, sem linha do tempo, nada pra expandir. A ideia é não parecer distraído na frente da pessoa.
2. **Revisão (fora da conversa):**
   - Quando o app está em repouso (ninguém na frente, nada gravando), aparece só uma linha discreta no canto superior esquerdo: **ÚLTIMOS REGISTROS ⌄**, que abre a lista para baixo, sempre dentro da zona de visão.
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

Visual: monocromático (verde e transparência sobre o preto), sem dicas de gesto e sem redundância. **Nunca fundo preto/overlay escuro** (preto é transparente nos óculos): se o card precisar de fundo, é **verde a 20% de opacidade**. Sem sombra preta no texto.

## Como o nome é descoberto na conversa (10/10)

No Brasil quase ninguém diz "meu nome é…". A apresentação é curta: **"Marcos, prazer"**, "Prazer, Ana", "esse é o Rafa". Regras:

- **"Prazer" é o gatilho principal.** O nome colado a ele (antes ou depois) é o da pessoa na sua frente. Vale nos dois sentidos: ela diz "Ana, prazer" ou você diz "Prazer, Ana".
- **Outros gatilhos:**
  - "meu nome é", "me chamo", "sou o/a…";
  - você apresentando alguém: "esse é o…", "o nome dela é…";
  - você falando com a pessoa: "você é o Marcelo", "então você é o…".
- **A fala chega sem vírgula e às vezes em minúsculas** ("marcelo campos é prazer"). As regras aceitam palavras de enchimento entre o nome e o "prazer" (é, né, então).
- **App real:** se nenhuma regra pegar, a IA (Gemini) lê a transcrição no fim do encontro e sugere o nome.
- **Falar de terceiros não conta.** "O João me disse…", "Aí, Carolina, tem que conhecer", "sua mãe, a Karin": nenhum desses vira o nome de quem está na frente. Nome depois de um parentesco (mãe, irmã, amigo…) é sempre de outra pessoa.
- **Confirmação de grafia:** "Marcus com u" corrige a grafia de um nome já salvo (Marcos → Marcus) e mantém o sobrenome.
- **Controle do usuário:** o nome ouvido entra direto e aparece no card na hora. Se veio errado, corrige no Manager ou falando a grafia.

## Pessoa sem nome = descrição acessível (10/10)

Ninguém é "desconhecido" nem "não cadastrado". Enquanto não há nome, o título é uma **descrição curta**, no estilo de audiodescrição: *"Moça de ~20 anos, cabelo castanho, de preto"*. Abaixo vem onde e quando vocês se conheceram: *"conheceu ontem · no café, falaram da viagem"*. Quando você reencontra a pessoa sem lembrar o nome, o card diz exatamente isso.

- **Subtítulo = o gancho da conversa:** a IA escolhe o que melhor marca o encontro, como um tema, uma situação engraçada ou uma frase. Exemplo: título *"Mulher de ~25 anos, cabelo roxo"*, subtítulo *"a menina da fofoca do relógio falso"*. Sem gancho, mostra *"conheceu ontem · resumo"*.
- **Card em 4 blocos (Marcus, 10/10):** título · tags (2–3) · ocasião (gancho) · sobre (~4 linhas). Contrato e prompt completos em `docs/PROMPT-ANALYZE-ENCOUNTER.md`.
- **Para o Beto:** `analyze_encounter` devolve também o campo `hook`: uma frase curta, no gênero da descrição, sem nome. Fica salvo em `custom_fields.hook` da pessoa e em `hook` do encontro.
- **Fonte (emulador):** idade e gênero estimados pela IA de rosto, cor do cabelo (ou careca) e cor predominante da roupa. Tom de pele fica de fora por enquanto, porque a luz engana demais.
- **Fonte (app real):** a mesma descrição, melhorada pela IA (Gemini) a partir da foto.
- **Onde fica:** `custom_fields.look`, com `status = Pendente`. O campo `nickname` é o apelido que a própria pessoa usa ("pode me chamar de Dani"). Quando o nome é ouvido, ele passa a ser o título e o status vira `Identificado`. A descrição continua guardada.
- **Registros antigos sem descrição:** são descritos no próximo encontro.
- **No Evento (grupo),** a descrição não aparece no HUD. Ela fica no registro do evento.

## Lugares: card preso ao prédio (proposta, 10/10, pode ser desligado)

Você anda pela Paulista, olha na direção do MASP e aparece o card **MASP**. Se você virar a cabeça, o card fica preso ao prédio. O espírito é Minority Report, sem poluir.

- **Reconhecer o lugar sem câmera:** GPS do celular + direção da cabeça (bússola/IMU dos óculos) + uma lista de pontos do entorno (OpenStreetMap/Wikidata) baixada por bairro. Custa poucos KB, não precisa de vídeo nem de IA de imagem. A câmera entra só para confirmar quando a bússola estiver em dúvida (fase 2).
- **O card mistura o público e o pessoal:** "MASP · museu, 1968" + **a sua memória ali**: "você esteve aqui em 12/03 com a Carla". O diferencial do VisualContext é lembrar, não fazer turismo.
- **Preso ao prédio (3 graus de liberdade):** a posição do card na tela = direção do prédio − direção da cabeça, recalculada ~60×/s com a IMU. Saiu do campo de visão → some (ou vira uma setinha na borda). Na altura, fica sempre dentro da zona dos 70%.
- **Onde precisa rodar:** **nos óculos**, porque 60 quadros/s não atravessam o Bluetooth. Como plugin Nexus puro não dá (a imagem atualiza no máximo a cada 150 ms e os plugins não rodam código nos óculos). Precisa de app nativo nos óculos (a Opção B do estudo do Anezium) ou de uma capacidade nova no Nexus ("card ancorado por direção"), boa proposta para ele.
- **Direção da cabeça (checado no código do Nexus em 10/10):** o Nexus **não** entrega aos plugins a direção da cabeça (bússola/IMU dos óculos). Nenhum código dele lê esses sensores; o mapa de navegação dele gira com a direção que o celular manda. A bússola do celular no bolso não serve para Lugares. Caminhos para a versão 0.1: (1) um app pequeno nos óculos, instalado por ADB, que só lê a direção da cabeça e manda para o celular (recomendado); (2) pedir ao Anezium uma capacidade "direção da cabeça" no Nexus (boa contribuição, mas o prazo é dele).
- **Limite honesto:** a bússola erra 10–20° perto de prédios e de metal. O card pode "escorregar" do prédio. Confirmar pela câmera de vez em quando resolve.
- **Controle (decisão do Marcus, 10/10):** Ajustes tem só **4 chaves principais: Lugares, Rostos, Coisas e Memórias**. **Não há subconfiguração** dentro de Lugares (nem quantidade de cards, nem nível de detalhe). Quem não gostar desliga a chave Lugares. *(Substitui os ajustes "cards 1–3" e "detalhe 1–5" de antes.)*
- **Comportamento fixo:** qualquer lugar por perto que esteja no campo de visão aparece sozinho, sem precisar mirar. **No máximo 2 cards ao mesmo tempo, campo de visão de 24° (o dos óculos)**; com 2, os cards ficam menores e nunca se sobrepõem. Se houver mais lugares do que espaço, vencem os mais centrais, com vantagem para os marcos (Wikipédia/Wikidata, museus, teatros, monumentos). O card mostra nome · tipo/endereço · distância · a sua memória ali.
- **Atrás de prédios = contorno pontilhado:** o lugar continua aparecendo, mas com contorno pontilhado e o texto "atrás de prédios" ao lado da distância. O cálculo usa os contornos e as alturas dos prédios do OpenStreetMap (ou ~3 andares quando a altura não está no mapa). Se um prédio no caminho passa da linha entre os olhos e o topo do lugar, o lugar está escondido. O prédio onde você está e o próprio prédio do lugar não contam. Altura dos olhos: 1,6 m (rua). Numa janela alta o cálculo exagera e marca coisas que você vê por cima dos prédios. Isso fica para depois.
- **Protótipo:** `web/lugares.html` (celular: GPS + bússola; ou simulação na Paulista). Lugares do OpenStreetMap: museus, teatros, cinemas, centros culturais, bibliotecas, templos, parques, monumentos.
- **Dados e bateria:** dados quase zero: a lista de pontos (poucos KB) e os prédios num raio de 400 m (algumas centenas de KB numa região densa, baixados uma vez e guardados no celular). A bateria pesa mais pela IMU e pela tela acesa do que pela rede.

## Encontro em grupo = Evento (proposta, 10/10, a decidir)

Com 3 ou mais rostos estáveis no quadro, ou sem um rosto dominante, o app não tenta adivinhar com quem você fala. Ele grava um **Evento**, uma lembrança coletiva, em vez de um encontro 1:1.

- **O registro:** hora, lugar (GPS do celular), duração, transcrição com falantes separados (Pessoa 1, 2, 3…), resumo e lista de participantes.
- **Rostos:** nada de vídeo contínuo. Uma foto parada a cada ~10 s; o **celular** detecta e compara todos os rostos dessa foto (barato: ~20 ms por rosto).
  - Conhecidos entram como participantes ("Ana, Rafael + 4 pessoas").
  - Desconhecidos **não** são cadastrados sozinhos. Ficam como rostos anônimos do evento; você nomeia no Manager se quiser, e eles somem depois de X dias se não forem nomeados.
- **Com quem você está falando:** a câmera acompanha a cabeça, então "olhar" ≈ rosto **central + maior + estável por ~2 s**. Só ele ganha um **rótulo pequeno com o nome** (sem card) no HUD. O resto não aparece.
- **Nome dos falantes, estilo Fathom:** a IA separa os falantes no áudio e depois dá nome a cada um por:
  - apresentações ("meu nome é…");
  - nomes ditos por outros ("Ana, o que você acha?");
  - quem estava no centro do quadro quando aquela voz falava (fase 2).
- **Divisão de trabalho:**
  - óculos: só detectam se há rosto e capturam as fotos;
  - celular: comparação dos rostos e gravação;
  - nuvem: separação de falantes e resumo.
- **Banda:** o Bluetooth leva só metadados e áudio (16 kHz mono ≈ 32 KB/s, cabe). As fotos vão pelo Wi‑Fi Direct, como no Nexus. Vídeo contínuo pelo Bluetooth não dá.
- **Microfone:** o dos óculos foca na boca de quem usa. Em grupo, o **celular na mesa** capta melhor a conversa.
- **HUD:** status "evento · 5 pessoas" no canto. Sem cards.

## Privacidade e bloqueio (10/10)

> Capítulo completo (manifesto, ameaças, criptografia, retenção, autodestruição, situações sensíveis, ajustes): `docs/PRIVACIDADE-E-SEGURANCA.md`.

Os óculos gravam a sua vida, inclusive momentos íntimos. Se outra pessoa colocar os óculos, o VisualContext não pode funcionar.

**Bloqueio:**
- **Tirou dos óculos, fechou.** O app trava e para de registrar e de mostrar qualquer coisa.
  - Hoje não temos sensor de uso confirmado. Até lá, as alternativas são:
    - o sensor de movimento: óculos parados por N segundos contam como "tirou";
    - a perda da conexão Bluetooth com o celular, que também trava.
  - Se o Rokid tiver sensor de uso (detecção de "no rosto"), ele substitui as duas.
- **Pra reativar, só pelo celular:** digital, reconhecimento facial do celular ou senha forte.
- **Palavra mágica por voz:** opcional e só como conveniência. Quem estiver perto ouve, então ela não substitui o desbloqueio no celular.

**Armazenamento:**
- Tudo criptografado no aparelho. Nuvem ainda em aberto: se existir, só como backup criptografado com a chave no seu aparelho.
- **Guardar pouco e leve:**

| O quê | Guarda |
|---|---|
| Conversa | só a transcrição (texto). Nunca áudio ou vídeo |
| Pessoa | a foto do rosto (pra comparar) |
| Moto / chave | o frame do último lugar onde foi visto |
| "Registra isso" | o texto lido; a imagem em baixa qualidade, preto e branco |

**Consequência pro backend:** hoje tudo fica num backend aberto, sem login, que qualquer pessoa lê, altera e apaga. Isso é o oposto dessa regra. Fechar o backend é pré-requisito de tudo. A decisão "nuvem ou só no aparelho" muda a arquitetura do Beto e precisa ser tomada antes do APK.

## Skills do MVP: rosto, chave e veículo (10/10)

O VisualContext não é só sobre pessoas. O MVP terá quatro skills de memória, e cada uma tem o seu card no HUD, sem coluna de ícones fixa na tela:

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

### Trajeto e localização: o que dá para pegar do Google Maps (10/10)

- **O histórico do Google Maps (Linha do tempo) não tem API.** Desde 2024–2025 ele fica **só no celular** (com backup criptografado opcional), e não há acesso programático nem em tempo real.
- **Passado:** importação **manual, uma vez**. O usuário vai em Ajustes → Local → Serviços de localização → Linha do tempo → *Exportar dados da Linha do tempo*, gera um **.json** e importa no Manager. O VisualContext lê as visitas e os trajetos e cruza com encontros ("com quem você estava no dia 12/03 no MASP").
- **Tempo real:** o **nosso app** registra a própria localização no celular (Fused Location) e reconhece atividades (veículo → caminhando = **moto estacionada**, que é o que o próprio Google Maps faz para "lugar onde estacionou"). Fica tudo no aparelho, criptografado, com a mesma retenção do capítulo de privacidade.
- **Navegar até a moto:** o app abre o **Google Maps** já com o destino (link `google.navigation:q=lat,lng&mode=w`), e o plugin **Navigation do Nexus** mostra as curvas no HUD. Não reinventamos navegação.

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

- Receitas e documentos são privados como todo o resto; ver **Privacidade e bloqueio**.

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

## Manager · página inicial = "Pergunte qualquer coisa" (10/10)

Por padrão, a página inicial do Manager é limpa: **um campo de texto**, como no Claude ou no Gemini. Você pergunta em linguagem natural e a IA responde com base na sua memória, mostrando de onde tirou cada resposta (qual encontro, qual dia). Exemplos:

- "Com quem eu almocei ontem?"
- "Quais amigos eu não vejo há mais de 3 meses?"
- "O que a Carla me contou do emprego novo?"
- "Onde deixei a moto?"

**Catálogo opcional:** para quem prefere navegar, existe a lista de pessoas, encontros e registros, com campos fechados e filtros. Ele fica um clique abaixo, não na frente.

**Invocar, não vigiar:** a memória aparece quando você pede (pergunta no Manager, "mostre os últimos encontros") ou quando reencontra alguém. Fora isso, fica quieta.

**Fotos:** sempre **um único frame por pessoa**, para reconhecer o rosto e mostrar "visto pela última vez". Um momento (como as crianças na piscina) vira **só uma descrição da cena**, sem imagem.

**Mesma memória nos óculos (10/10):** perguntar à memória é **um motor só** (`ask_memory`), chamado de dois lugares. A memória mora no celular (e na nuvem, se ativada); os óculos só perguntam e mostram.

| | Óculos | Manager (celular/laptop) |
|---|---|---|
| Como pergunta | Voz, pelo botão de IA (via Nexus `assistant`) ou frase de ativação | Texto ou voz |
| Contexto extra | O que está na frente: "quem é ela?" usa o rosto atual | Nenhum |
| Resposta | **Curta**: 1–3 linhas, dentro da zona dos 70%. Se for uma pessoa, o próprio card. | Completa, com as fontes (encontro, dia) |
| Voz de volta | Desligada por padrão (em público, só na tela) | — |
| Resposta longa | Mostra o essencial + "mais no celular" | — |
| Sem fonte | "Não encontrei" (nunca palpite) | Idem |

**Para o Beto (backend):** ação `ask_memory` no fcProxy. Ela recebe a pergunta e busca nos encontros e nas pessoas (por texto e, depois, por embeddings). Responde com as fontes. Mesmo modelo e mesma chave do `analyze_encounter`.

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
