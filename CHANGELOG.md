# Changelog

Todas as mudanças relevantes serão documentadas neste arquivo. O formato segue
[Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto usa versionamento semântico.

## [Unreleased]

### Adicionado

- `--skip-tests`/`skipTests` ignoram todos os testes; `--skip-test`/`excludeTests` ignoram arquivos,
  pastas ou globs de testes (`ctest -E` e `meson --no-suite` em backends externos).
- Targets ASM com NASM, GNU assembler e MASM, inclusive combinados com C e C++.
- Ferramentas globais/per-target, formatos de saída, objetos e binários puros, e plataforma por target.
- Alias `package` e seleção `--target` consistente em build, clean, test e install.
- Testes de comandos, lifecycle, cache ASM e integração nativa/cross com workflow Linux/Windows.

- Lockfile opcional `BuildGraph.lock.json`.
- Comando `lock` na CLI e no modo interativo.
- Profiles Maven para testes reais com GCC, Clang e MSVC.
- Políticas de segurança e suporte.

### Alterado

- Dependências de executáveis/binários estabelecem ordem sem link automático.
- O cache nativo inclui plataforma e ferramentas; o estado anterior é reconstruído.
- Flags de compilação C/C++, montagem e link são aplicadas somente às etapas correspondentes.

- `refresh` passa a atualizar o lock implicitamente.
- Build incremental relinka quando bibliotecas precompiladas de packages mudam.

## [1.1.6] - 2026-11-13

### Adicionado

- Placeholder `${profile.name}` (e `${profile.current.name}`) com o nome do profile ativo.
- `"command": "${buildgraph.command}"` em tasks executa o próprio BuildGraph, sem depender do `PATH`.

### Alterado

- Admissão de compilações pela memória disponível a cada unidade, em vez de um orçamento fixado no
  início; reserva padrão de 1/16 da RAM (entre 512 MiB e 2 GiB).
- Estimativa de memória por unidade calibrada (C++: 256 MiB + 512 bytes por byte de fonte) e pico
  medido usado diretamente quando existe.
- `PATH`/`PATHEXT` saem da assinatura incremental e do cache quando a identidade do GCC cobre as
  ferramentas; `TMP`, `TEMP`, `TMPDIR`, `LANGUAGE` e `LC_*` sem efeito no objeto saem sempre.
  Trocar de shell não recompila mais o projeto.
- Código usa imports em vez de nomes totalmente qualificados.

## [1.1.5] - 2026-11-06

### Adicionado

- Compilação paralela por arquivo com scheduler global compartilhado entre targets.
- Controle de CPU e RAM com jobs automáticos, orçamento, reserva e estimativas por compilação.
- Cache compartilhado persistente de objetos com SHA-256, escrita atômica, integridade e quota LRU.
- Profiling opcional, response files para comandos longos e testes com milhares de fontes.
- Ícones nativos no empacotamento Windows, Linux e macOS, com geração automática de ICNS.

### Alterado

- Verificação incremental por conteúdo, objetos com nomes estáveis e estado concorrente.
- Cancelamento com encerramento dos subprocessos e sincronização antes de salvar o estado.
- Empacotamento e preparação dos ícones executados diretamente no workflow, sem pasta tools.
- Comentários de código e Javadocs removidos.
