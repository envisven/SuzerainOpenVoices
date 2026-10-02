# Sordland Open Voices

Sordland Open Voices is an offline JavaFX viewer for exploring the structure of the Sordland campaign in *Suzerain*. It renders campaign progression, dialogue graphs, decisions, conditions, effects, variable writes, runtime source information, and source provenance without modifying the game data.

The Java package and internal IntelliJ module retain the historical `SordlandTreeViewer` name. The repository name is **SordlandOpenVoices**.

## Requirements

- BellSoft Liberica JDK 25 FULL, or another JDK 25 distribution that includes JavaFX
- The Suzerain JSON data generated locally by the user

The repository does **not** contain generated game-data dumps.

## Game data

Place the locally generated files in `data/` using these names:

- `SuzerainDataDumper.entity_data.json`
- `SuzerainDataDumper.conversations_Sordland.json`
- `SuzerainDataDumper.actor_names.json` (optional)

Optional runtime data can be placed under `data/runtime/`.

You can also launch with a different data directory:

```sh
./scripts/run.sh --data=/absolute/path/to/data
```

On startup, if the required entity or conversation file is not found, the application opens a file chooser instead of modifying or downloading data.

## Run from a terminal

macOS / Linux:

```sh
./scripts/run.sh
```

Windows:

```bat
scripts\run.cmd
```

The scripts first use `JAVA_HOME` when it points to a JDK with JavaFX. They can also use a suitable `javac` on `PATH`; macOS additionally checks the system JDK registry through `/usr/libexec/java_home`. No user-specific home directory is hardcoded.

Build without launching:

```sh
./scripts/build.sh
```

Windows:

```bat
scripts\build.cmd
```

## IntelliJ IDEA

Shared IntelliJ project files are included in `.idea/`, `.run/`, and `SordlandTreeViewer.iml`.

1. Open the repository folder in IntelliJ IDEA.
2. Set the Project SDK to BellSoft Liberica JDK 25 FULL or another JavaFX-enabled JDK 25.
3. Run the checked-in **Sordland Tree Viewer** configuration.

The **Headless Checks** run configuration is also included.

## Main features

- ROOTED campaign view based on Sordland GameFlow data
- PLAIN source catalogue view
- dialogue-level rooted graphs
- event, decision, bill, condition, choice, news, effect, and runtime-source inspection
- actor and type filtering
- variable search and Variable Inspector
- numeric gain / loss / set analysis
- compact causal rendering
- optional logic labels and source evidence
- exact provenance tracking
- shared **Jump to source** mode between the main viewer and Variable Inspector

## Tests

With the required local game data present:

```sh
./scripts/test.sh
```

Windows:

```bat
scripts\test.cmd
```

Desktop JavaFX smoke-test scripts are also available in `scripts/`.

## Repository layout

| Path | Purpose |
| --- | --- |
| `src/` | Application source code |
| `tests/` | Regression and validation checks |
| `scripts/` | Build, run, and test scripts |
| `data/` | Local data location; JSON dumps are ignored by Git |
| `docs/` | Design and validation notes |
| `.run/` | Shared IntelliJ run configurations |

Generated build output is written to `build/` and ignored by Git.

## License

MIT License. See `LICENSE`.
