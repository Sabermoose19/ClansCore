# Third-party components

The source uses these pinned public dependencies. Their original notices are carried in the respective artifacts; consult upstream licenses before redistributing modified bundles.

| Component | Version | Use / upstream |
|---|---|---|
| Paper API | 26.1.2.build.74-stable | Provided by server; [PaperMC](https://github.com/PaperMC/Paper) |
| SQLite JDBC | 3.50.3.0 | Bundled in ClansCore; [Xerial](https://github.com/xerial/sqlite-jdbc), Apache-2.0/BSD-2-Clause; SQLite engine public domain |
| SLF4J API | Transitive from SQLite JDBC | Bundled API; [SLF4J](https://www.slf4j.org/license.html), MIT |
| PlaceholderAPI API | 2.12.3 | Provided compile dependency, not bundled; [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI), upstream GPL-3.0 |
| JUnit Jupiter | 5.13.4 | Tests only; [JUnit](https://junit.org/), EPL-2.0 |
| Maven wrapper | 3.3.4 | Included build bootstrap; [Apache Maven](https://maven.apache.org/wrapper/), Apache-2.0 |
| Apache Maven | 3.9.11 | Downloaded by wrapper; Apache-2.0 |

Adventure and SnakeYAML used by production/test sources come from the provided Paper API dependency graph; runtime implementations are supplied by the server. No downloaded Minecraft server, JDK, formatter, or third-party runtime plugin JAR is included in the source archive. LPC was inspected but not compiled or executed; runtime test-plugin downloads were removed. The SQLite native libraries required by its JDBC driver are included in ClansCore's shaded JAR.
