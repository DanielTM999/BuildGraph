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
