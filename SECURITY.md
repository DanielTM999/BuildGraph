# Política de segurança

## Versões suportadas

Somente a release estável mais recente recebe correções de segurança. O branch `main` é de
desenvolvimento e não deve ser tratado como uma versão suportada.

## Reportando uma vulnerabilidade

Não abra uma issue pública. Use um
[GitHub Security Advisory privado](../../security/advisories/new) e informe:

- Versão e sistema operacional.
- Manifest mínimo ou passos para reprodução.
- Impacto observado e impacto esperado.
- Logs sem credenciais ou dados sensíveis.

O projeto fará uma confirmação inicial em até cinco dias úteis. Prazos de correção e divulgação
serão definidos conforme severidade, explorabilidade e disponibilidade de mitigação.

## Modelo de confiança

Manifests são considerados código confiável. Tasks `before` e `after` executam comandos arbitrários
com as permissões do usuário que iniciou o BuildGraph. Revise manifests de terceiros antes de
executar `build`, `test`, `install`, `refresh` ou o modo interativo.
