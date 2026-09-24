# Changelog

Todas as mudanças relevantes serão documentadas neste arquivo. O formato segue
[Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto usa versionamento semântico.

## [Unreleased]

### Adicionado

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
