# Política de suporte

## Versões

O suporte cobre apenas a release estável mais recente. Correções são entregues em uma nova release;
builds arbitrários do branch `main` não possuem garantia de estabilidade.

## Plataformas

Os pacotes nativos são produzidos para:

- Windows x64.
- Linux x64.
- macOS x64.

O build direto possui testes de integração locais para GCC, Clang e MSVC, executados pelos profiles
Maven documentados no README. Toolchains, versões ou arquiteturas fora dessa lista são best effort.

## Limites

O suporte do BuildGraph cobre parsing do manifest, resolução/materialização de packages, geração de
comandos, incrementalidade e lifecycle. Erros internos do compilador, código do projeto, SDKs e
bibliotecas de terceiros devem ser reproduzidos fora do BuildGraph antes de serem reportados.

Tasks do manifest são comandos do próprio projeto e permanecem sob responsabilidade de quem mantém
o manifest.
