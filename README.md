# BuildGraph

BuildGraph é uma ferramenta de build para projetos C e C++. Ela pode compilar projetos descritos
por `Manifest.json`/`Manifest.xml` ou delegar o build para CMake, Meson e Make. Também oferece
profiles incrementais, resolução de packages, tarefas de lifecycle e repositórios locais.

## Conteúdo

- [Download](#download)
- [Uso da CLI](#uso)
- [Compilação incremental](#compilação-incremental)
- [Modo interativo](#modo-interativo)
- [Integração com clangd](#integração-com-clangd)
- [Formatos de saída](#formatos-de-saída-e-exit-codes)
- [Manifest](#manifest)
- [Targets](#targets)
- [Compiladores e caminhos](#compiladores-e-caminhos-dos-executáveis)
- [Sistemas de build](#sistemas-de-build)
- [Fontes, testes e artefatos](#fontes-testes-e-artefatos)
- [Lifecycle e instalação](#lifecycle-e-instalação)
- [Assembly e projetos mistos](#assembly-e-projetos-mistos)
- [Build a partir do código-fonte](#build-a-partir-do-código-fonte)
- [Versionamento e releases](#versionamento-e-releases)
- [Licença](#licença)

## Download

Os pacotes para Windows, Linux e macOS são gerados automaticamente para tags SemVer (`v1.2.3`) e
publicados em uma GitHub Release. Baixe a versão mais recente na página de
[Releases](../../releases/latest). Os pacotes produzidos por `jpackage` já incluem o runtime Java.

Depois de extrair:

- Windows: execute `BuildGraph\BuildGraph.exe`.
- Linux: execute `BuildGraph/bin/BuildGraph`.
- macOS: execute `BuildGraph.app/Contents/MacOS/BuildGraph`.

O uber JAR também pode ser usado diretamente, desde que o Java 25 ou superior esteja instalado:

```shell
java -jar BuildGraph.jar --help
```

## Uso

```text
buildgraph [projectPath] [clean] [build] [test] [install] [refresh] [lock]
           [--interactive] [-f raw|json|xml]
           [-p profile] [-c compiler]
           [--test-main arquivo]
           [-t target] [-j jobs]
           [--repo caminho] [--packages caminho] [--compile-commands]
           [--no-incremental]
```

Se `projectPath` não for informado, o diretório atual será usado. Sem uma fase explícita,
BuildGraph executa `build`.

| Comando/opção | Função |
| --- | --- |
| `clean` | Remove o diretório de build. |
| `build` | Compila o projeto. |
| `test` | Executa build e testes. |
| `package` | Alias de `build`; aceita a mesma seleção por `--target`. |
| `install` | Executa build e publica o projeto no primeiro repositório configurado. |
| `refresh` | Resolve e materializa os packages do manifest. |
| `lock` | Resolve versões e grava `BuildGraph.lock.json` sem materializar packages. |
| `--interactive` | Monitora o manifest e aceita comandos pela entrada padrão. |
| `-p`, `--profile` | Profile usado somente quando o manifest não define `activeProfile`. |
| `-c`, `--compiler` | Compilador usado somente quando o manifest não define compiladores. |
| `--test-main` | Arquivo que define `main()`, relativo a `testFolder`; fallback de `testMain`. |
| `--repo`, `--external` | Repositório adicional, depois dos repositórios do manifest. |
| `--packages`, `--out` | Pasta local usada quando `packagesBase` não está configurado. |
| `--compile-commands`, `--clangd` | Imprime um `compile_commands.json` resolvido no stdout e encerra. |
| `-t`, `--target` | Limita build/package, clean, install e bibliotecas dos testes ao target e suas dependências. Repetível ou separado por vírgula. |
| `-j`, `--jobs` | Limita o paralelismo entre targets (`-j 1` = serial). Default: número de CPUs. |
| `--no-incremental` | Ignora o estado incremental, recompila todas as fontes e regrava o cache. |

Exemplos:

```shell
# Build do projeto no diretório atual
BuildGraph build

# Limpa e depois compila outro projeto
BuildGraph ../meu-projeto clean build

# Usa saída JSON para integração com outra ferramenta
BuildGraph . build --format json

# Usa um profile da CLI somente se activeProfile não existir no manifest
BuildGraph . test --profile debug

# Usa um repositório adicional
BuildGraph . refresh --repo D:/buildgraph-repository

# Atualiza explicitamente apenas o lock de dependências
BuildGraph . lock

# Força recompilação completa sem apagar outros arquivos do outputDir
BuildGraph . build --no-incremental
```

As fases solicitadas são sempre ordenadas como `clean → build → test → install`, mesmo que tenham
sido escritas em outra ordem. `install` implica `build`; `test` também implica `build` para sistemas
externos e manifests multi-target.

Durante o build, o progresso é exibido como `[atual/total]`, começando em `[0/n]`. Em builds
diretos, cada fonte compilada e a etapa final de link/archive contam como uma unidade e mostram
sua duração. O mesmo formato é usado nos modos normal e interativo, inclusive com targets
executados em paralelo.

### Compilação incremental

Builds diretos por manifest ou descoberta automática são incrementais por padrão. O estado fica em
`<outputDir>/.buildgraph-state`: cada objeto é invalidado quando mudam o conteúdo da fonte ou de
um header, o comando de compilação, o modo de build ou a toolchain. GCC/Clang geram dependências
com `-MMD -MF`; MSVC usa `/showIncludes`. O link/archive só é repetido quando os objetos, uma
biblioteca dependente ou o comando de link mudam. `clean` remove também esse estado.

```shell
# Primeiro build: compila e linka normalmente
BuildGraph . build

# Sem mudanças: reutiliza objetos e artefato
BuildGraph . build

# Ignora o estado nesta execução, recompila tudo e grava um estado novo
BuildGraph . build --no-incremental

# Remove outputDir, incluindo objetos e estado incremental, antes do build
BuildGraph . clean build
```

CMake, Meson e Make continuam responsáveis pela própria incrementalidade; `--no-incremental`
controla somente a compilação direta executada pelo BuildGraph.

## Modo interativo

O modo interativo mantém o processo aberto, lê comandos pela entrada padrão e reutiliza o mesmo
contexto de projeto. Ele é útil para IDEs, scripts persistentes e ciclos rápidos de desenvolvimento.

```shell
BuildGraph caminho/do/projeto --interactive
```

Também aceita as opções normais:

```shell
BuildGraph . --interactive --format json --profile debug --repo D:/packages
BuildGraph . --interactive --no-incremental
```

No primeiro comando, cada `build` usa o cache incremental. No segundo, todos os builds daquela
sessão recompilam as fontes; a opção é definida ao iniciar a sessão e não pode ser alternada por um
comando interativo.

Ao iniciar, o modo interativo:

1. Localiza e valida o manifest.
2. Mostra os diagnósticos encontrados.
3. Executa um `refresh` inicial dos packages.
4. Começa a aguardar um comando por linha na entrada padrão.

| Comando interativo | Comportamento |
| --- | --- |
| `build` | Recarrega a configuração efetiva e executa a fase de build. |
| `clean` | Remove o diretório de build efetivo. |
| `test` | Executa build e depois os testes. |
| `install` | Executa build e publica o projeto no primeiro repositório. |
| `refresh` | Resolve novamente os packages e atualiza a pasta materializada. |
| `lock` | Atualiza `BuildGraph.lock.json` sem materializar packages. |
| `reload` | Relê o manifest e imprime seus diagnósticos, sem executar build. |
| `status` | Mostra projeto, build system detectado e toolchain selecionada. |
| `compile-commands`, `clangd` ou `compdb` | Emite a compilation database resolvida como JSON bruto. |
| `help` ou `?` | Mostra os comandos aceitos. |
| `quit`, `exit` ou `q` | Encerra a sessão. |

Exemplo de ciclo incremental dentro da sessão:

```text
> build
> build
> clean build
> refresh
> lock
> quit
```

O segundo `build` reutiliza os resultados do primeiro quando não houve mudanças. `clean build`
força um rebuild ao remover o diretório de saída e seu estado incremental.

Linhas vazias são ignoradas. Um comando desconhecido gera um aviso e mantém a sessão aberta.

O watcher monitora criação e alteração de `Manifest.json`/`Manifest.xml` diretamente na raiz do
projeto, com debounce de 250 ms. Quando detecta uma mudança, relê os diagnósticos e executa
`refresh`. Um manifest dentro de `.buildgraph` continua válido para build, mas atualmente não é
observado automaticamente; nesse caso use `reload` e `refresh` após alterá-lo.

Exemplo de controle por pipe no PowerShell:

```powershell
"status`nbuild`nquit" | .\BuildGraph\BuildGraph.exe . --interactive
```

Com `--format json` ou `--format xml`, todas as respostas continuam usando o formato escolhido, o
que permite controlar a sessão por outro processo sem analisar texto colorido.

## Integração com clangd

clangd utiliza uma compilation database chamada `compile_commands.json` para descobrir o
compilador, linguagem, standards, includes, defines e flags de cada arquivo. BuildGraph gera esse
conteúdo a partir da configuração efetiva, evitando que uma IDE ou plugin precise interpretar o
manifest por conta própria.

### CLI de execução única

Use `--compile-commands`; `--clangd` e `--compdb` são aliases:

```shell
BuildGraph . --compile-commands > compile_commands.json
clangd --compile-commands-dir=.
```

O stdout contém somente o array JSON da compilation database, sem prefixos, cores ou mensagens de
log. Erros são enviados para stderr e retornam exit code `1`. `--format` não altera esse payload.

A resolução considera:

- Manifest raiz e profile ativo.
- `cCompiler` e `cxxCompiler`, com `--compiler` como fallback.
- `cStandard`, `cxxStandard`, sysroot, target, defines e flags de compilação.
- `sources` e extensões de cada fonte.
- Includes do projeto e de packages materializados.
- `Debug`/`Release` e o `outputDir` efetivo.

É emitida uma entrada por fonte, usando o campo `arguments` para evitar problemas de escaping de
shell. Cada entrada contém `directory`, `file`, `arguments` e `output`.

Se existirem packages declarados, BuildGraph executa sua resolução silenciosamente antes de gerar
o JSON, garantindo que os include paths materializados estejam disponíveis. Em projetos CMake,
Meson ou Make, se `<outputDir>/compile_commands.json` já existir, BuildGraph retorna esse arquivo
normalizado em uma única linha; caso contrário, gera uma base a partir do manifest e das fontes
detectadas.

### Modo interativo

Durante uma sessão, envie qualquer um destes comandos:

```text
compile-commands
clangd
compdb
```

A resposta é o array JSON bruto em uma única mensagem no stdout, independentemente do formato de
logs da sessão. O programa integrador pode enviar `compile-commands`, ler o próximo payload JSON e
gravá-lo como `compile_commands.json` antes de iniciar ou recarregar o clangd.

Exemplo conceitual de integração:

```text
IDE/plugin -> stdin do BuildGraph:  compile-commands\n
BuildGraph -> stdout:              [{"directory":"...","file":"...","arguments":[...]}]
IDE/plugin:                        grava o JSON e inicia/recarrega clangd
```

Alterações no manifest são consideradas na próxima solicitação, pois a configuração efetiva é
recalculada a cada comando.

## Formatos de saída e exit codes

O formato padrão é `raw`, com severidade e cores ANSI. Para automação, use um formato estruturado:

- `--format json`: um objeto JSON por linha (NDJSON), com `severity`, `message` e timestamp `ts`.
- `--format xml`: um elemento `<log>` por linha, com atributos `severity` e `ts`. É um stream de
  elementos, não um único documento XML com elemento raiz.

Exemplo NDJSON:

```json
{"severity":"INFO","message":"Scanning for projects...","ts":1710000000000}
```

No modo de execução única, os exit codes principais são:

| Código | Significado |
| --- | --- |
| `0` | Operação concluída com sucesso. |
| `1` | Falha de refresh, build, teste, task ou instalação. |
| `2` | Argumento, formato ou caminho de projeto inválido. |

O modo interativo mantém a sessão após falhas de comandos individuais e encerra normalmente com
`quit` ou fim da entrada padrão.

## Manifest

O manifest pode ficar na raiz ou em `.buildgraph`, nos formatos JSON ou XML. A ordem de procura é:

1. `Manifest.json`
2. `Manifest.xml`
3. `.buildgraph/Manifest.json`
4. `.buildgraph/Manifest.xml`

Exemplo JSON:

```json
{
  "id": "hello",
  "name": "hello",
  "version": "1.0.0",
  "cStandard": "c17",
  "cxxStandard": "c++20",
  "cCompiler": "gcc",
  "cxxCompiler": "g++",
  "sources": ["src"],
  "includes": ["include"],
  "defines": ["APP_VERSION=1"],
  "compileFlags": ["-Wall"],
  "linkFlags": [],
  "outputDir": "build",
  "packagesBase": ".buildgraph/packages",
  "repositories": ["../packages-repository"],
  "properties": {
    "generatedDir": "generated"
  },
  "env": {
    "APP_ENV": "development"
  },
  "packages": [
    {
      "id": "fmt",
      "version": "10.2.1",
      "downloadUrl": "https://servidor.exemplo/fmt-10.2.1.zip"
    }
  ],
  "activeProfile": "debug",
  "profiles": {
    "debug": {
      "buildType": "Debug",
      "defines": ["DEBUG=1"],
      "packages": [
        { "id": "test-lib", "version": "1.0.0" }
      ]
    },
    "release": {
      "buildType": "Release",
      "defines": ["NDEBUG"]
    }
  }
}
```

### Referência dos campos raiz

Campos desconhecidos são ignorados. Listas e mapas nulos são tratados como vazios.

| Campo | Tipo | Finalidade |
| --- | --- | --- |
| `id` | string | Identificador estável usado por packages e por `install`. |
| `name` | string | Nome do projeto e nome-base do artefato. Se ausente, usa `id` ou a pasta do projeto. |
| `version` | string | Versão publicada por `install`. |
| `description` | string | Descrição propagada ao manifest da biblioteca instalada. |
| `library` | boolean | Gera biblioteca compartilhada em vez de executável no build direto. |
| `cCompiler` | string | Nome ou caminho do executável do compilador C. |
| `cxxCompiler` | string | Nome ou caminho do executável do compilador C++. |
| `cStandard` | string | Padrão C, por exemplo `c11`, `c17` ou `c23`. |
| `cxxStandard` | string | Padrão C++, por exemplo `c++17`, `c++20` ou `c++23`. |
| `compilerVersion` | string | Fallback legado para o padrão da linguagem; não é o caminho do compilador. |
| `platform` | string | Arquitetura/triple de destino; herdado pelos targets. Ausente ou inválido usa a máquina local. |
| `toolchainVersion` | string | Metadata de versão da toolchain disponível para profiles/placeholders. |
| `sysroot` | string | Caminho passado como `--sysroot` em compiladores compatíveis. |
| `sources` | string[] | Pastas ou arquivos de fonte C/C++ relativos ao projeto. |
| `testFolder` | string | Pasta de fontes de teste relativa ao projeto. Ausente ou vazia usa `<projeto>/tests`. |
| `testMain` | string | Arquivo com `main()`, relativo a `testFolder`. Ausente ou vazio procura exatamente um `main()` na pasta. |
| `includes` | string[] | Pastas de headers adicionadas à linha de compilação. |
| `defines` | string[] | Macros; `-D` ou `/D` é acrescentado quando necessário. |
| `compileFlags` | string[] | Argumentos extras inseridos na compilação. |
| `linkFlags` | string[] | Argumentos extras inseridos na etapa de link GCC/Clang. |
| `libraryPaths` | string[] | Pastas de bibliotecas adicionadas com `-L`. |
| `outputDir` | string | Pasta de build; o padrão é `<projeto>/build`. |
| `packagesBase` | string | Pasta local onde dependências resolvidas são materializadas. |
| `repositories` | string[] | Pastas pesquisadas para localizar packages. |
| `packages` | object[] | Dependências do projeto. |
| `targets` | object[] | Targets de build (múltiplos executáveis e bibliotecas). Veja [Targets](#targets). |
| `activeProfile` | string | Nome do profile efetivo. |
| `profiles` | object | Profiles disponíveis, indexados pelo nome. |
| `properties` | object | Valores livres usados por placeholders. |
| `env` | object | Variáveis de ambiente fornecidas a compiladores e tasks. |
| `tasks` | object[] | Comandos associados às fases do lifecycle. |

O mesmo modelo pode ser escrito em XML. Exemplo mínimo:

```xml
<Manifest>
  <id>hello</id>
  <name>hello</name>
  <version>1.0.0</version>
  <cCompiler>gcc</cCompiler>
  <cxxCompiler>g++</cxxCompiler>
  <sources>src</sources>
  <includes>include</includes>
  <repositories>../packages-repository</repositories>
</Manifest>
```

Quando `outputDir` estiver ausente, `null`, vazio ou contiver apenas espaços, o build será gerado
em `<projeto>/build`.

### Precedência

Configurações do manifest têm precedência sobre a CLI:

1. Manifest raiz e profile ativo.
2. Opção correspondente da CLI, usada como fallback.
3. Valor padrão.

Isso se aplica ao profile ativo, compiladores, `packagesBase` e repositórios. Formato de saída e
fases do lifecycle continuam sendo controlados pela CLI.

### Profiles incrementais

Listas do profile são combinadas com as listas do manifest raiz. Por exemplo, se a raiz declara o
package `A` e o profile declara `B`, o resultado será `A+B`. Se ambos declararem o mesmo `id`, a
entrada do profile substitui a entrada raiz.

Também podem ser usados `excludeIncludes`, `excludeSources` e `excludeDefines` para
remover valores herdados. `env` e `properties` são combinados por chave, com o profile prevalecendo.

Regras completas de composição:

- Scalars como compilador, padrão, plataforma, `outputDir` e `packagesBase`: o valor não vazio do
  profile substitui o valor raiz.
- `library`: o valor do profile substitui a raiz somente quando declarado.
- `sources`, `includes`, `defines`, `libraryPaths` e `repositories`: união ordenada sem
  duplicatas.
- `compileFlags` e `linkFlags`: concatenação raiz + profile, preservando inclusive duplicatas.
- `packages`: união por `id`; uma entrada do profile substitui a raiz quando o `id` é igual.
- `env` e `properties`: merge por chave; o profile prevalece.

Campos aceitos dentro de um profile incluem `buildType`, `platform`, `toolchainVersion`,
`compilerVersion`, `cStandard`, `cxxStandard`, `sysroot`, `cCompiler`, `cxxCompiler`, `outputDir`,
`packagesBase`, `library`, as listas de compilação, `repositories`, `packages`, `env` e
`properties`.

### Targets

Um projeto pode declarar múltiplos targets — vários executáveis e bibliotecas na mesma pasta,
com fontes compartilhadas e saídas independentes. Sem `targets`, o comportamento clássico é
mantido: um único artefato controlado por `library`.

```json
{
  "id": "vema",
  "version": "0.1.0",
  "sources": ["src/shared"],
  "includes": ["include"],
  "targets": [
    { "id": "vema-core", "type": "shared", "sources": ["src/core"],
      "defines": ["VEMA_CORE_BUILD"] },
    { "id": "vema-jit",  "type": "static", "sources": ["src/jit"],
      "dependsOn": ["vema-core"] },
    { "id": "vema",      "type": "executable", "sources": ["src/runtime"],
      "dependsOn": ["vema-core", "vema-jit"] },
    { "id": "vemac",     "type": "executable", "sources": ["src/compiler"],
      "dependsOn": ["vema-core"] }
  ],
  "profiles": {
    "windows": {
      "targets": [ { "id": "vema", "defines": ["VEMA_WIN32"] } ]
    }
  }
}
```

Campos de cada target:

| Campo | Tipo | Semântica |
| --- | --- | --- |
| `id` | string | Obrigatório e único. Identifica o target no grafo, em `--target` e na pasta de intermediários. |
| `name` | string | Nome-base do artefato; default é o `id`. |
| `type` | string | `executable` (default), `shared`, `static`, `object` (uma fonte) ou `binary` (binário puro). |
| `sources` | string[] | Somadas às da raiz; `excludeSources` remove herdadas. |
| `includes`, `defines` | string[] | Somados aos da raiz; `excludeIncludes`/`excludeDefines` removem herdados. |
| `compileFlags`, `asmFlags`, `linkFlags` | string[] | Concatenados aos da raiz; aplicados respectivamente a C/C++, ASM e link. |
| `platform` | string | Destino do target; sobrescreve raiz/profile. Ausente ou inválido usa a máquina local. |
| `asmFormat` | string | Formato do assembler: `auto` (default), `bin`, `elf32`, `elf64`, `win32`, `win64`, `macho32` ou `macho64`, conforme o destino e a ferramenta. |
| `outputName` | string | Caminho relativo da saída dentro de `outputDir`; default segue nome, tipo e plataforma do target. |
| `linkMode` | string | `auto` (default), `driver` ou `linker`. |
| `linkDependencies` | string[] | Arquivos adicionais, como linker scripts, rastreados pelo cache do link. |
| `libraryPaths` | string[] | Somados aos da raiz. |
| `dependsOn` | string[] | Ids de outros targets: define a ordem de build e o link automático. |

Regras:

- **Herança**: cada target herda o manifesto efetivo (raiz + profile ativo) com as mesmas regras
  incrementais dos profiles. Declare as pastas compartilhadas na raiz e as específicas em cada
  target. Em modo multi-target não há fallback para `src`/raiz: um target sem `sources`
  efetivos é erro.
- **Profiles × targets**: `profiles.<nome>.targets` é combinado por `id` com os targets da raiz
  (listas somam, scalars sobrescrevem); um `id` novo adiciona um target. A ordem final de
  composição é raiz → profile → target → override do profile no target.
- **Dependências**: `dependsOn` constrói o alvo antes. Bibliotecas e objetos participam do link
  pelos caminhos dos artefatos; DLLs com linker MSVC usam a import library. Executáveis e binários
  estabelecem somente ordem. Objetos já incorporados em bibliotecas não são repetidos no link.
  Includes são herdados das dependências linkadas. Ciclos e IDs desconhecidos são erros.
- **Paralelismo**: targets independentes no grafo compilam em paralelo por padrão (limite =
  número de CPUs). `-j N` limita; `-j 1` força serial. Na primeira falha nenhum target novo é
  iniciado (fail-fast). Logs são prefixados com `[targetId]`.
- **Saída**: todos os artefatos finais ficam planos em `outputDir` (o exe encontra as DLLs ao
  lado); objetos intermediários de targets static ficam em `<outputDir>/.obj/<targetId>/`.
  Colisão de nome de artefato entre targets é erro.
- **Static**: exige um archiver — `llvm-ar` (procurado ao lado do compilador), `ar` no PATH ou
  `lib.exe` no MSVC.
- **`install`**: publica cada target `shared`/`static` como package `<id-do-projeto>-<id-do-target>`
  com a versão do projeto, contendo o artefato e os `includes` do target. Executáveis não são
  publicados.
- **`test`**: os testes compilam com as fontes compartilhadas da raiz e linkam contra os
  artefatos das bibliotecas do projeto.
- **`--compile-commands`**: emite uma entrada por fonte com as flags do target; em fontes
  compartilhadas o primeiro target declarado vence.
- **`library` + `targets`**: se ambos aparecem, `targets` prevalece e um warning é emitido.

Limitações conhecidas: com toolchain MSVC (`cl.exe`), linkar contra um target `shared` requer a
import lib `<name>.lib` no `outputDir` (com clang/MinGW o link direto funciona; o clang em target
MSVC gera a import lib automaticamente quando há símbolos exportados via `__declspec(dllexport)`).

### Repositórios

`repositories` aceita uma ou mais pastas. Caminhos relativos são resolvidos a partir da raiz do
projeto. A ordem final de resolução é:

1. Repositórios do manifest raiz.
2. Repositórios adicionados pelo profile ativo.
3. Repositório informado por `--repo`.
4. Repositório global `~/.buildgraph/repository`.

A busca ocorre nessa ordem. Downloads e `install` escrevem no primeiro repositório. Os packages
materializados para o projeto ficam em `packagesBase` ou, por padrão,
`<projeto>/.buildgraph/packages`.

Cada package declarado aceita:

| Campo | Obrigatório | Finalidade |
| --- | --- | --- |
| `id` | sim | Identificador do package. |
| `version` | não | Versão exata. Pode ser usada sozinha ou junto de `versionConstraint`. |
| `downloadUrl` | quando ainda não instalado | URL de um ZIP que será baixado e normalizado. |
| `versionConstraint` | não | Versão exata ou range (`[1.0,2.0)`, `>=1.2 <2.0`, `^1.2.3`, `~1.2`, `1.4.x`). |
| `transitive` | não | Propaga dependências declaradas no `Manifest.json` da biblioteca; padrão `true`. |

Exemplo com versão exata, range e bloqueio de transitividade:

```json
{
  "packages": [
    {
      "id": "fmt",
      "versionConstraint": "[10.0,12.0)"
    },
    {
      "id": "zlib",
      "version": "1.3.1",
      "downloadUrl": "https://example.org/zlib-1.3.1.zip",
      "transitive": false
    }
  ]
}
```

São aceitas versões exatas e constraints como `[1.0,2.0)`, `>=1.2 <2.0`, `^1.2.3`, `~1.2` e
`1.4.x`. Quando mais de uma dependência restringe o mesmo package, o resolvedor tenta uma versão
que satisfaça todas as origens e retorna erro explícito se não houver solução.

O layout do repositório é:

```text
<repo>/<id>/<version>/<variante>/
├── Manifest.json
└── lib/
```

Para resolução, BuildGraph escolhe a maior versão instalada que satisfaça todas as constraints,
propaga as dependências da biblioteca e interrompe com um erro que identifica as origens quando
há conflito. Em seguida procura primeiro uma variante específica solicitada internamente, depois
`source` e por fim a primeira variante válida. Se uma versão exata não existir em nenhum
repositório, `downloadUrl` deve apontar para um ZIP. O download é instalado no primeiro
repositório e depois copiado para `packagesBase`.

O `Manifest.json` de uma biblioteca pode declarar dependências transitivas no campo
`dependencies` (o alias `packages` também é aceito), usando o mesmo formato da lista acima.

```json
{
  "id": "wrapper",
  "version": "1.0.0",
  "dependencies": [
    { "id": "core", "versionConstraint": "^2.0" }
  ]
}
```

Na CLI, `BuildGraph . refresh` resolve, atualiza o lock e materializa dependências. `build`, `test`
e `install` apenas sincronizam packages usando o lock válido ou a resolução temporária do manifest.

### Lock de dependências

`BuildGraph.lock.json` registra as versões exatas, variantes e relações transitivas resolvidas. O
arquivo é sempre JSON, mesmo quando o projeto usa `Manifest.xml`, e deve ser versionado junto com o
projeto.

```shell
# Resolve e grava o lock, sem alterar a pasta local de packages
BuildGraph . lock

# Resolve, atualiza o lock e materializa packages
BuildGraph . refresh
```

`build`, `test` e `install` usam o lock quando ele existe e corresponde às declarações do manifest.
Se estiver ausente ou desatualizado, o BuildGraph emite um aviso, resolve diretamente do manifest e
continua sem modificar o lock; esse build funciona, mas não tem garantia de resolução reproduzível.
Um lock com JSON inválido é tratado como erro e precisa ser removido ou regenerado com `lock` ou
`refresh`.

No modo interativo, `lock` apenas atualiza o arquivo e `refresh` também materializa packages. A
inicialização da sessão e alterações observadas no manifest apenas sincronizam packages usando o
lock ou o fallback ao manifest, sem reescrever o arquivo automaticamente.

### Tarefas do lifecycle

Tarefas podem executar comandos antes ou depois de `clean`, `build`, `test` e `install`:

```json
{
  "tasks": [
    {
      "id": "generate",
      "phase": "build",
      "when": "before",
      "order": 10,
      "command": "generator",
      "args": ["--output", "${properties.generatedDir}"],
      "workingDir": "${project.dir}",
      "failOnError": true
    }
  ]
}
```

Uma tarefa pode declarar `dependsOn`, `env` e argumentos. `dependsOn` ordena tarefas da mesma
fase/posição; `order` desempata as tarefas disponíveis. Um ciclo gera aviso e usa a ordem
declarada. `failOnError` é `true` por padrão; quando `false`, o lifecycle continua após uma saída
não zero.

No XML, `tasks` é o bloco da coleção e cada item usa o elemento `task`:

```xml
<tasks>
  <task>
    <id>prepare</id>
    <phase>build</phase>
    <when>after</when>
    <command>generator</command>
    <args>--output</args>
    <args>${properties.generatedDir}</args>
  </task>
  <task>
    <id>publish</id>
    <phase>build</phase>
    <when>after</when>
    <dependsOn>prepare</dependsOn>
    <command>publisher</command>
  </task>
</tasks>
```

São suportados placeholders como
`${project.dir}`, `${project.id}`, `${project.version}`, `${env.NOME}`,
`${properties.chave}` e `${profile.current.propriedade}`.

## Compiladores e caminhos dos executáveis

`cCompiler`, `cxxCompiler` e `--compiler` representam o nome ou o caminho para o **executável/binário
do compilador**, não a pasta que contém o compilador e não sua versão.

### Configuração no manifest

Use campos separados quando C e C++ possuem drivers diferentes:

```json
{
  "cCompiler": "/usr/bin/gcc",
  "cxxCompiler": "/usr/bin/g++"
}
```

Windows com MinGW:

```json
{
  "cCompiler": "C:/mingw64/bin/gcc.exe",
  "cxxCompiler": "C:/mingw64/bin/g++.exe"
}
```

Windows com LLVM:

```json
{
  "cCompiler": "C:/Program Files/LLVM/bin/clang.exe",
  "cxxCompiler": "C:/Program Files/LLVM/bin/clang++.exe"
}
```

MSVC, após abrir um Developer Command Prompt ou carregar o ambiente do Visual Studio:

```json
{
  "cCompiler": "cl.exe",
  "cxxCompiler": "cl.exe"
}
```

O valor pode ser:

- Um comando disponível no `PATH`, como `gcc`, `g++`, `clang`, `clang++` ou `cl`.
- Um caminho absoluto até o executável, opção recomendada para builds reproduzíveis.
- Um caminho relativo; se não estiver no `PATH`, sua resolução dependerá do diretório do projeto e
  do sistema operacional. Prefira caminho absoluto para evitar ambiguidade.

Em JSON, use `/` nos caminhos Windows ou escape cada barra invertida:

```json
{
  "cCompiler": "C:\\mingw64\\bin\\gcc.exe"
}
```

Se apenas `cCompiler` for informado, ele também será usado como fallback para C++. Se apenas
`cxxCompiler` for informado, ele será usado como fallback para C. Para projetos mistos, declare os
dois campos explicitamente.

Profiles podem selecionar uma toolchain diferente:

```json
{
  "cCompiler": "/usr/bin/gcc",
  "cxxCompiler": "/usr/bin/g++",
  "profiles": {
    "windows": {
      "cCompiler": "C:/mingw64/bin/gcc.exe",
      "cxxCompiler": "C:/mingw64/bin/g++.exe"
    }
  },
  "activeProfile": "windows"
}
```

### Configuração pela CLI

`--compiler`/`-c` recebe um único executável e o utiliza como driver tanto para C quanto para C++:

```shell
BuildGraph . build --compiler clang++
BuildGraph . build --compiler /opt/llvm/bin/clang++
BuildGraph . build --compiler "C:\Program Files\LLVM\bin\clang++.exe"
```

Como a CLI aceita somente um driver, para projetos que precisam de `gcc` para C e `g++` para C++,
configure `cCompiler` e `cxxCompiler` no manifest. Em projetos C++ usando GCC, prefira `g++` na CLI
para que a biblioteca padrão C++ seja ligada corretamente.

Os compiladores do manifest/profile têm precedência. `--compiler` só é usado quando nem
`cCompiler` nem `cxxCompiler` estão definidos no manifest efetivo.

`compilerVersion` não seleciona o executável. Ele funciona como fallback para o padrão de
linguagem. Para evitar ambiguidade, prefira `cStandard` e `cxxStandard`:

```json
{
  "cStandard": "c17",
  "cxxStandard": "c++20"
}
```

Sem configuração explícita, a detecção no `PATH` tenta, em ordem: Clang (`clang`/`clang++`),
GCC/MinGW (`gcc`/`g++`) e MSVC (`cl`).

> `cCompiler`, `cxxCompiler`, standards, flags e includes controlam o build direto pelo manifest.
> Projetos CMake, Meson ou Make são delegados às respectivas ferramentas; configure o compilador
> também pelo mecanismo do build system, como toolchain file, variáveis `CC`/`CXX` ou configuração
> do próprio projeto.

## Sistemas de build

BuildGraph detecta o sistema usando o primeiro arquivo aplicável:

| Prioridade | Arquivo | Build | Teste |
| --- | --- | --- | --- |
| 1 | `CMakeLists.txt` | `cmake -S . -B <buildDir>` e `cmake --build <buildDir>` | `ctest --test-dir <buildDir> --output-on-failure` |
| 2 | `meson.build` | `meson setup <buildDir>` e `ninja -C <buildDir>` | `meson test -C <buildDir> --print-errorlogs` |
| 3 | `Makefile`, `makefile` ou `GNUmakefile` | `make` | `make test` |
| 4 | Manifest existente | Compilação direta C/C++ | Compilação e execução direta dos testes |
| 5 | Nenhum dos anteriores | Compilação direta com descoberta automática de fontes | Teste direto |

No CMake, o profile define `buildType` e o valor é enviado como `CMAKE_BUILD_TYPE`. Para Meson e
Make, a configuração detalhada permanece sob controle do projeto externo.

## Fontes, testes e artefatos

No build direto, as fontes são procuradas da seguinte maneira:

1. Entradas declaradas em `sources` — cada uma pode ser uma pasta (percorrida recursivamente) ou
   o caminho de um arquivo de fonte individual.
2. A pasta `src`, quando existe e `sources` está vazio.
3. A raiz do projeto como último fallback.

São reconhecidos arquivos `.c`, `.cpp`, `.cc`, `.cxx`, `.c++`, módulos C++ (`.cppm`, `.ixx`,
`.mpp`, `.ccm`, `.cxxm`) e Objective-C/Objective-C++ (`.m`, `.mm`). Pastas de metadata e saída,
como `.git`, `.buildgraph`, `.idea`, `build`, `out` e `cmake-build-*`, são ignoradas.

Para testes diretos, fontes sob `test` e `tests` são compiladas junto com as fontes do projeto,
exceto arquivos cujo nome começa com `main.`. O executável de teste é iniciado e seu exit code
define o resultado. Se nenhuma fonte de teste existir, a fase termina com sucesso.

No build direto, executáveis usam `name`, depois `id`, depois o nome da pasta do projeto. Bibliotecas
com `library: true` geram `.dll` no Windows, `.dylib` no macOS e `.so` no Linux. Todos são escritos
em `outputDir`, cujo padrão é `<projeto>/build`.

Os modos são:

- `Debug` por padrão: `-O0 -g` em GCC/Clang ou `/Od /Zi` em MSVC.
- `Release` quando o profile define `buildType: "Release"`: `-O2` e `NDEBUG`.

## Lifecycle e instalação

Cada fase executa suas tasks `before`, depois a operação da fase e finalmente suas tasks `after`.
Uma falha interrompe as fases seguintes.

`package` é um alias de `build`, inclusive nas tasks e no modo interativo; não cria um arquivo
de distribuição nem uma segunda execução dos hooks. `build package` executa a fase uma vez.

```shell
buildGraph package --target app
buildGraph clean package --target app
buildGraph install --target core
buildGraph test --target core
```

Com `--target`, a cadeia inclui o alvo e suas dependências transitivas. `clean` remove apenas
suas saídas, intermediários e cache, incluindo nomes de saída anteriores registrados pelo build.
`install` publica somente bibliotecas dessa cadeia. `test` mantém a suíte global do projeto,
mas restringe os objetos e bibliotecas usados no link à cadeia selecionada. Um teste global que
precise de outra biblioteca exige incluí-la na seleção. Tasks e packages externos continuam globais.
Os backends CMake/Meson/Make rejeitam `--target`; essa seleção pertence ao build direto.

No modo interativo, `package --target app` substitui a seleção inicial apenas para aquela linha.

Sem seleção, `clean` remove todo o diretório de build efetivo. `install` exige `id` ou `name` e também `version`.
Ele publica os includes existentes e o artefato compilado em:

```text
<primeiro-repositório>/<id>/<version>/source/
├── Manifest.json
└── lib/
```

Esse package passa a poder ser resolvido por outros projetos que apontem para o mesmo repositório.

## Build a partir do código-fonte

### Assembly e projetos mistos

C, C++ e ASM podem coexistir no mesmo projeto e no mesmo target, em qualquer combinação.
Cada fonte usa sua própria ferramenta: `.asm` usa NASM por padrão; `.s`/`.S` usam GNU assembler.
Para fontes MASM, configure `asmKind: "masm"`. Um target usa uma família de assembler; para
combinar dialetos incompatíveis, declare targets separados e conecte seus objetos/bibliotecas.
Fontes `.S` exigem pré-processamento GCC/Clang; demais fontes ASM não exigem compilador C/C++.

Os campos abaixo aceitam nome no PATH ou caminho de executável; argumentos ficam nas listas de flags:

| Campo | Uso |
| --- | --- |
| `asmCompiler` | Executável do assembler; exemplos: `nasm`, `aarch64-linux-gnu-as`, `ml64.exe`. |
| `asmKind` | `nasm`, `gas` ou `masm`; inferido para nomes conhecidos, obrigatório para wrappers personalizados. |
| `linker` | Executável de link direto; exemplos: `ld`, `ld.lld`, `link.exe`, `lld-link.exe`. |
| `linkerKind` | `gnu`, `msvc` ou `darwin`; inferido para nomes conhecidos. |
| `archiver` | Executável para bibliotecas estáticas, como `ar`, `llvm-ar` ou `lib.exe`. |
| `objcopy` | Conversão da imagem linkada para binário puro; exemplos: `objcopy`, `llvm-objcopy`. |

Esses campos, `asmFlags` e `linkDependencies` podem ser declarados na raiz, nos profiles e nos
targets. Os targets herdam os valores globais e podem sobrescrevê-los. JSON e XML têm o mesmo
comportamento; listas XML usam `<asmFlags><asmFlag>...</asmFlag></asmFlags>` e
`<linkDependencies><linkDependency>...</linkDependency></linkDependencies>`.

```json
{
  "cCompiler": "gcc",
  "cxxCompiler": "g++",
  "asmCompiler": "nasm",
  "linker": "ld",
  "platform": "x86_64-linux-gnu",
  "targets": [
    { "id": "boot", "type": "binary", "sources": ["boot/boot.asm"],
      "asmFormat": "bin", "outputName": "boot.bin" },
    { "id": "fast", "type": "object", "sources": ["asm/fast.asm"] },
    { "id": "app", "type": "executable", "sources": ["src/main.cpp", "src/support.c"],
      "dependsOn": ["fast"], "asmFlags": ["-g"], "linkFlags": ["-pthread"] }
  ]
}
```

`linkMode: auto` escolhe o driver C++ se houver C++ no target ou nas dependências linkadas,
o driver C quando houver C e o linker global para ASM puro. `linkMode: linker` força o linker
direto também para C/C++; nesse modo, configure runtime, bibliotecas e entry point necessários
em `linkFlags`. Flags são argumentos literais, sem tradução entre sintaxes de drivers e linkers.

`type: binary` com NASM e `asmFormat: bin` monta uma única fonte principal diretamente, sem linker.
Use `%include` para dividir esse programa em vários arquivos. Nos demais casos, o build monta/compila
os objetos, linka uma imagem intermediária e chama `objcopy -O binary`. Por exemplo, um kernel
pode usar `linkMode: linker`, `linkFlags: ["-T", "kernel.ld"]` e
`linkDependencies: ["kernel.ld"]`. `type: object` aceita uma fonte; vários objetos podem ser
agrupados em targets `static` ou linkados em `executable`, `shared` e `binary`.

`platform` aceita arquiteturas como `x86_64`, `arm64` e `riscv64`, ou triples como
`aarch64-linux-gnu`, `riscv32-none-elf` e `x86_64-pc-windows-msvc`. Triples têm vocabulário de
arquitetura aberto; a toolchain determina os destinos disponíveis. Ausência usa o host; valor
inválido gera aviso e usa o host. Ferramenta incompatível com um destino válido produz erro,
sem retornar silenciosamente ao host. NASM e MASM continuam limitados às arquiteturas próprias
dessas ferramentas. Para cross-compilation, configure os executáveis cruzados ou disponibilize
os nomes `<triple>-as`, `<triple>-ld`, `<triple>-ar` e `<triple>-objcopy` no PATH.

Includes ASM são rastreados por depfiles NASM/GNU. MASM remonta conservadoramente quando não há
dependências confiáveis. O cache também considera ferramentas, formato e plataforma; caches de
versões anteriores são reconstruídos. `compile_commands.json` continua dedicado às fontes C/C++.

### Compilar o BuildGraph

Requisitos:

- JDK 25 ou superior.
- Maven.

```shell
mvn clean package
java -jar target/BuildGraph.jar --help
```

O artefato `target/BuildGraph.jar` é um uber JAR executável com todas as dependências.

Testes reais de toolchain são opt-in e exigem que o compilador esteja disponível no ambiente:

```shell
mvn verify -Pnative-it-gcc
mvn verify -Pnative-it-clang
mvn verify -Pnative-it-msvc
```

Para MSVC, execute o comando em um Developer Command Prompt ou carregue o ambiente do Visual
Studio antes do Maven. Ausência da toolchain faz o profile falhar explicitamente.

Os testes `AssemblyToolchainNativeIT` verificam NASM, GNU, MASM e um binário misto com Clang/LLD.
Backends ausentes são reportados como testes ignorados. Para configurar caminhos explicitamente:

```shell
mvn verify -DskipNativeITs=false -Dit.test=AssemblyToolchainNativeIT -Dbuildgraph.it.nasm=/path/to/nasm
```

Também estão disponíveis `buildgraph.it.clang`, `buildgraph.it.ld`, `buildgraph.it.objcopy`,
`buildgraph.it.masm`, `buildgraph.it.gas`, `buildgraph.it.gasCompiler` e `buildgraph.it.gasPlatform`. O workflow
`native-toolchains.yml` cobre Linux/GNU/NASM e Windows/MSVC/MASM, além de montagem cruzada RISC-V.

O pacote Java raiz é `dtm.builder`; integrações que iniciam a classe principal diretamente devem
usar `dtm.builder.Main`.

## Versionamento e releases

O build Maven local usa `1.0.0-SNAPSHOT` por padrão. O workflow de publicação aceita somente tags
SemVer e usa a própria tag como versão do Maven, do `jpackage`, dos arquivos e da GitHub Release:

```shell
git tag v1.2.3
git push origin v1.2.3
```

Tags de prerelease, como `v1.2.3-rc.1`, também são aceitas. Pushes comuns em branches não publicam
releases. Alterações relevantes devem ser registradas no [CHANGELOG.md](CHANGELOG.md).

## Licença

BuildGraph é software de código aberto distribuído sob a [licença MIT](LICENSE).
Consulte também [SECURITY.md](SECURITY.md), [SUPPORT.md](SUPPORT.md) e
[CHANGELOG.md](CHANGELOG.md).
