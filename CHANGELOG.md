# Changelog

Todas as mudanças relevantes serão documentadas neste arquivo. O formato segue
[Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto usa versionamento semântico.

## [Unreleased]

### Adicionado

- Lockfile opcional `BuildGraph.lock.json`.
- Comando `lock` na CLI e no modo interativo.
- Profiles Maven para testes reais com GCC, Clang e MSVC.
- Políticas de segurança e suporte.

### Alterado

- `refresh` passa a atualizar o lock implicitamente.
- Build incremental relinka quando bibliotecas precompiladas de packages mudam.
