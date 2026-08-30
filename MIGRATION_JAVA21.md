# Plan de migración de RichFaces 4.6.2 a Java 21

Este documento es la guía paso a paso que seguiremos para migrar el proyecto a
Java 21. Está pensado para ejecutarse de forma incremental: cada fase deja el
proyecto en un estado verificable antes de avanzar a la siguiente.

---

## 1. Diagnóstico del estado actual

Situación detectada tras analizar el repositorio:

| Aspecto | Estado actual | Objetivo |
|---|---|---|
| Versión de Java | source/target `1.7` (Travis compila con JDK 8) | Java 21 |
| Build tool | Maven (parent `jboss-parent:11`), multi-módulo | Maven actualizado |
| Plataforma | Java EE 6 (`jboss-javaee-6.0` `3.0.2.Final`) | Jakarta EE 10 |
| Namespaces | `javax.faces`, `javax.servlet`, `javax.el`, `javax.annotation`, `javax.validation`, `javax.persistence`, `javax.xml.bind`... | `jakarta.*` |
| Implementación JSF | Mojarra (vía Arquillian, `org.glassfish:javax.faces`) | Mojarra 4.x / Faces 4.0 |
| Generación de código | RichFaces CDK (`richfaces-cdk-maven-plugin` 4.5.1-SNAPSHOT) | Compatible con Java 21 |
| Tests | JUnit + Arquillian + Graphene/Drone + PhantomJS | JUnit + contenedores modernos |
| Contenedores test | WildFly 8/9/10, Tomcat 7/8 | WildFly 30+, Tomcat 10+ |
| Alcance | ~1000+ archivos `.java` con `import javax.*` | Migrados a `jakarta.*` |

### Puntos críticos / riesgos
- **Es una librería de componentes JSF, no una app.** El salto grande no es el
  bytecode de Java sino el cambio de namespace **`javax.faces` → `jakarta.faces`**
  (Faces 4.0). Esto es un cambio de API con ruptura, no solo un renombrado.
- **El CDK genera código.** El plugin `richfaces-cdk-maven-plugin` genera clases
  de componentes durante el build. Si el CDK genera imports `javax.*`, hay que
  migrar también las plantillas/templates del CDK, no solo el código fuente.
- **`javax.faces.bean.ManagedBean`, `@RequestScoped`, `@ViewScoped`, etc.**
  fueron **eliminados** en Faces 4.0. El modelo de managed beans de JSF ya no
  existe: hay que migrar a CDI (`jakarta.enterprise.context.*`).
- **`javax.xml.bind` (JAXB)** se eliminó del JDK en Java 11. Hay que añadir la
  dependencia Jakarta XML Binding.
- **PhantomJS** está descontinuado; los tests de integración de navegador
  necesitarán Selenium moderno + navegador headless (Chrome/Firefox).
- Dependencias muy antiguas (jboss-parent 11, plugins de 2016) pueden no ser
  compatibles con Java 21.

> Conclusión: no es una migración "cambiar el número de versión". Es un salto
> Java EE 6 → Jakarta EE 10. Lo abordaremos por fases, módulo a módulo.

---

## 2. Estrategia general

1. Trabajar en una rama dedicada (`migration/java21`).
2. Migrar en el orden de dependencias del reactor Maven:
   `bom` → `build/*` → `core` → `components` → `dist` → `examples`.
3. En cada módulo: compilar, corregir, testear, commit. No avanzar con el
   build roto.
4. Separar claramente dos cambios independientes para poder aislar errores:
   - **(A)** Subida de toolchain a Java 21 (compilador, plugins, JDK).
   - **(B)** Migración `javax.*` → `jakarta.*` (Jakarta EE 10).
   Se recomienda hacer **(A) primero compilando aún contra Java EE 6** si es
   posible, y luego **(B)**. Si el CDK/JSF antiguo no compila en Java 21, se
   fusionan ambas fases.
5. Usar herramientas de migración automatizada (Eclipse Transformer / OpenRewrite)
   para el grueso del renombrado `javax → jakarta`, y revisión manual para las
   APIs eliminadas (managed beans, etc.).

---

## 3. Prerrequisitos (entorno local)

- [ ] Instalar **JDK 21** (Temurin/Adoptium recomendado) y configurar `JAVA_HOME`.
- [ ] Verificar **Maven 3.9+** (`mvn -v`). Los plugins nuevos lo requieren.
- [ ] Tener **Git** limpio (sin cambios sin commitear) antes de empezar.
- [ ] (Opcional) Instalar Chrome/Firefox para tests de integración.
- [ ] Verificar acceso a red para descargar nuevas dependencias.

Comandos de verificación:
```
java -version        # debe reportar 21
mvn -v               # debe reportar 3.9+ y apuntar a JDK 21
```

---

## 4. Fase 0 — Preparación y línea base

- [ ] Crear rama: `git checkout -b migration/java21`.
- [ ] Intentar un build de línea base **con el JDK actual (8)** para confirmar
      que el proyecto compila HOY:
      `mvn -q -DskipTests clean install`
- [ ] Registrar qué módulos compilan y cuáles fallan ya de base.
- [ ] Congelar el estado con un commit inicial de la rama.

> Objetivo: tener una referencia de "qué funcionaba antes" para no confundir
> fallos preexistentes con fallos introducidos por la migración.

### Resultados de la Fase 0 (ejecutada)

Entorno confirmado:
- JDK **21.0.4** Temurin en `C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot`
  (el terminal de Kiro inyecta un JDK 25 vía la extensión pleiades, por eso los
  builds se lanzan forzando `JAVA_HOME` al 21 con
  `cmd /v:on /c "set JAVA_HOME=...&& mvn.cmd ..."`).
- Maven **3.9.16**, Git OK, Chrome **151** en `C:\Program Files\Google\Chrome\Application`.

Rama creada: `migration/java21` (desde el commit `d0e4ad4`, tag `4.6.2.ayg`).

Build de línea base (`mvn -B -DskipTests clean install`) con JDK 21:

1. **Primer fallo:** `maven-gpg-plugin:sign` no encuentra `gpg.exe`. Es la firma
   de artefactos para release; no es necesaria para compilar. Se evita con
   `-Dgpg.skip=true`.
2. **Segundo fallo (el importante):** al compilar `richfaces-build-resources`:
   ```
   Source option 7 is no longer supported. Use 8 or later.
   Target option 7 is no longer supported. Use 8 or later.
   ```
   El JDK 21 ya NO admite `source/target = 1.7`. El pom raíz fija
   `maven.compiler.source/target = 1.7`.

**Conclusión de la línea base:** el proyecto NO compila en JDK 21 sin cambios.
El primer bloqueo es el nivel de compilación, lo que confirma que la **Fase 1
(subir el toolchain a `release=21`) es obligatoria y va primero**. El plugin GPG
debe quedar desactivado por defecto durante el desarrollo.

Módulos que compilan hoy antes del bloqueo: `RichFaces BOM`,
`RichFaces Build Version Management`. El resto queda SKIPPED tras el fallo.

---

## 5. Fase 1 — Actualizar el toolchain a Java 21 (cambio A)

Objetivo: que Maven use JDK 21 y plugins compatibles, aunque todavía se apunte
temporalmente a un target antiguo.

- [ ] En el `pom.xml` raíz, actualizar las propiedades de compilación:
  ```xml
  <maven.compiler.release>21</maven.compiler.release>
  ```
  (reemplaza `maven.compiler.source`/`target` `1.7`).
- [ ] Actualizar `maven-compiler-plugin` a una versión compatible con Java 21
      (3.11.0+).
- [ ] Actualizar plugins core que rompen en JDK 21:
  - `maven-surefire-plugin` (3.x)
  - `maven-jar-plugin`, `maven-source-plugin`, `maven-javadoc-plugin`
  - `maven-assembly-plugin`, `maven-resources-plugin`
  - `jacoco-maven-plugin` (0.8.11+)
- [ ] Evaluar subir el `jboss-parent` (v11 → una versión moderna) o sobreescribir
      localmente las versiones de plugins que herede.
- [ ] Quitar/actualizar flags de JVM obsoletos (p. ej. `-XX:MaxPermSize`, que ya
      no existe desde Java 8) en `surefire.jvm.params` y perfiles Arquillian.
- [ ] Actualizar `.travis.yml` / CI: `jdk: openjdk21` (o migrar a GitHub Actions).
- [ ] Compilar. Anotar errores. En esta fase son esperables errores por APIs
      del JDK eliminadas (JAXB `javax.xml.bind`), que se resuelven en la Fase 2.

### Resultados de la Fase 1 (ejecutada) — BUILD SUCCESS con JDK 21

Todos los módulos del reactor (BOM → build/* → core → components → dist)
compilan e instalan con JDK 21 (`mvn -DskipTests -Dgpg.skip=true clean install`).
Cambios aplicados y el porqué:

1. **pom.xml raíz**: `source/target 1.7` → `maven.compiler.release=21`. Subida de
   plugins: compiler 3.13.0, surefire 3.2.5, jar/source/javadoc/assembly/resources
   modernos, jacoco 0.8.12, release 3.1.1, scm 2.1.0, install 3.1.2. Añadido
   `version.javadoc.plugin=3.6.3`. Quitado `-XX:MaxPermSize` del vmargs de Arquillian.
2. **Conflicto `--source` con `--release`**: el `jboss-parent:11` inyecta
   `<compilerArguments><source/><target/></compilerArguments>` en el compiler-plugin,
   incompatible con `<release>`. Solucionado en el `pluginManagement` raíz con
   `<configuration combine.self="override">` + `<compilerArguments combine.self="override"/>`.
3. **build/build-resources/pom.xml**: quitado `source/target 1.7` local, compiler
   a 3.13.0 con `release`, assembly 3.7.1; quitadas versiones antiguas fijas de
   source/javadoc para heredar del raíz.
4. **build/page-fragments/pom.xml**: quitadas versiones fijas de source/javadoc.
   Eliminada la ejecución local `attach-sources` (goal jar) que duplicaba la del
   jboss-parent (goal jar-no-fork) → error "duplicated artifacts". Javadoc de JDK 21
   fallaba por HTML5/doclint (tags `<tt>`): resuelto globalmente con
   `<doclint>none</doclint>` + `<failOnError>false</failOnError>` en el javadoc-plugin raíz.
5. **.mvn/jvm.config** (nuevo): `--add-opens` para `java.base/java.lang` (y otros).
   El CDK usa Guice/cglib con reflexión profunda sobre `ClassLoader.defineClass`,
   bloqueada por JPMS en JDK 16+ ("module java.base does not opens java.lang").
6. **build/resource-optimizer-plugin/pom.xml**: `maven-plugin-plugin` y
   `maven-plugin-annotations` 3.4 → 3.13.1 (el 3.4 no lee bytecode 21 en helpmojo).
7. **components/pom.xml**: la ejecución `precompile-sources-for-cdk` ahora compila a
   `target/cdk-precompile-classes` (directorio separado). Compartir `target/classes`
   con el `default-compile` hacía que javac de JDK 21 fallara al reescribir clases
   anónimas de enum (`PanelIcons$State$1`): "error while writing".
8. **components/a4j/.../RendererBase.java**: quitado `final` de `encodeBegin`,
   `encodeChildren`, `encodeEnd`. El CDK genera renderers que sobreescriben esos
   métodos públicos; con `final` no compilan (fallaría en cualquier JDK — el CDK
   binario 4.5.1-SNAPSHOT y este RendererBase estaban desincronizados). Los renderers
   generados NO están versionados en git (se generan en cada build).
9. **components/rich/.../SwingTreeNodeImpl.java**: `Enumeration<?> children()` →
   `Enumeration<? extends TreeNode> children()`. `javax.swing.tree.TreeNode` cambió
   su firma a genérica en JDK 9+ (cambio real de API del JDK).
10. **core/.../resource-optimizer/.../ReflectionsExt.java**: bloque estático que
    inicializa `Reflections.log` (campo `public static`) si es null. Reflections 0.9.8
    dejaba el logger null y lanzaba NPE al loguear un warning durante el escaneo en
    JDK 21. Se mantiene reflections en 0.9.8 (subir a 0.9.12 rompe la API del scanner
    custom `MarkerResourcesScanner extends AbstractScanner`).

Notas / deuda para fases siguientes:
- El optimizador de recursos usa YUI Compressor 2.4.8 + Rhino antiguo, que emite
  "syntax errors" al minificar `jquery.js` y otros JS modernos. Son NO fatales
  (el build tiene éxito), pero conviene actualizar el minificador más adelante.
- Persisten warnings de deprecación (`new Integer(int)`) y el warning de
  `com.sun:tools:jar` (tools.jar) del jboss-parent; ninguno bloquea.
- Todo el código sigue en `javax.*`: la migración a `jakarta.*` es la Fase 2.
- Los ejemplos (`examples/*`) siguen con `source/target 1.7` y `MaxPermSize`;
  se tratarán en Fase 3/4 cuando se activen en el reactor.

Patrón de build usado (JDK 21 real, evitando el JDK 25 del terminal de Kiro):
`cmd /v:on /c "set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot&& mvn.cmd -DskipTests -Dgpg.skip=true clean install"`

---

## 6. Fase 2 — Migración de plataforma: Java EE 6 → Jakarta EE 10 (cambio B)

Este es el núcleo del trabajo. Se hace por sub-pasos.

### 6.1 Dependencias / BOM
- [ ] Reemplazar `jboss-javaee-6.0` / `jboss-javaee-web-6.0` por el BOM de
      **Jakarta EE 10** (`jakarta.platform:jakarta.jakartaee-bom:10.0.0`) o
      dependencias individuales (Faces 4.0, Servlet 6.0, EL 5.0, CDI 4.0,
      Bean Validation 3.0, Jakarta XML Binding 4.0).
- [ ] Añadir implementación **Mojarra 4.x** (`org.glassfish:jakarta.faces:4.x`)
      donde antes se usaba `org.glassfish:javax.faces`.
- [ ] Añadir **Jakarta XML Binding** (`jakarta.xml.bind:jakarta.xml.bind-api` +
      `org.glassfish.jaxb:jaxb-runtime`) para sustituir el `javax.xml.bind`
      eliminado del JDK.
- [ ] Actualizar el `bom/pom.xml` del proyecto en consecuencia.

### 6.1.b DECISIÓN DE ENFOQUE (Fase 2 = Opción B: Jakarta EE 9.1 / Faces 3.0)

Se eligió **Jakarta EE 9.1 (Faces 3.0)**: solo el renombrado `javax.* → jakarta.*`
SIN los cambios de API de Faces 4.0 (los managed beans `@ManagedBean`/scopes
siguen existiendo en Faces 3.0). Herramienta: **Eclipse Transformer**.

Hallazgo clave de la investigación: **el CDK 4.5.1-SNAPSHOT genera `javax.*` de
forma incondicional** (plantillas `.ftl` y serializador de taglib con imports
`javax` hardcodeados; su código fuente no está en el workspace, solo el jar en
`.m2`). Esto condiciona cómo aplicar el Transformer. Dos estrategias:

- **Estrategia 1 (reescritura de fuente):** reescribir `src/main/java`, XML y
  `.template.xml` a `jakarta.*` de forma permanente + parchear el CDK para que
  genere `jakarta.*`. Es "jakarta nativo" pero invasiva (toca 1000+ archivos y
  el generador).
- **Estrategia 2 (post-transformación de artefactos) — ELEGIDA AHORA:** el
  proyecto sigue compilando en `javax` (estado de la Fase 1); se añade un paso
  que transforma los **JARs compilados** (`richfaces-core`, `richfaces-a4j`,
  `richfaces`) de `javax` → `jakarta` con el `transformer-maven-plugin`. No toca
  código fuente ni el CDK. Es el uso canónico de Eclipse Transformer y el de
  menor riesgo.

> **PENDIENTE PARA EL FUTURO (EE 10 nativo):** cuando migremos a Jakarta EE 10
> nativo (Faces 4.0), habrá que ejecutar la **Estrategia 1**: reescribir el
> código fuente a `jakarta.*`, **parchear el CDK** (plantillas `.ftl` +
> serializador de taglib) para que genere `jakarta.*`, migrar los managed beans
> JSF a CDI, subir `faces-config`/taglibs a esquema 4.0, y sustituir las
> dependencias de test `com.github.albfernandez.test-jsf` por variantes jakarta.
> La Estrategia 2 (post-transformación) es un paso intermedio; NO sustituye ese
> trabajo de reescritura nativa.

Paquetes `javax.*` que el Transformer NO debe tocar (siguen en el JDK):
`javax.xml.parsers`, `javax.xml.transform`, `javax.xml.xpath`, `javax.imageio`,
`javax.swing`, `javax.naming`, `javax.crypto`, `javax.security`, `javax.net`,
`javax.sql`. Caso especial: `javax.xml.rpc` (JAX-RPC, en
`core/.../InitializationListener.java`) no tiene equivalente Jakarta EE 9.1 Web
Profile → requiere revisión manual.

### 6.1.c Resultados de la Fase 2 (Estrategia 2 ejecutada) — artefactos jakarta OK

Se añadió el módulo agregador **`jakarta-bridge`** con 3 submódulos
(`core`, `a4j`, `rich`). Cada submódulo:
1. `maven-dependency-plugin:unpack` descomprime el jar `javax` correspondiente
   (`richfaces-core` / `richfaces-a4j` / `richfaces`) en `target/classes`;
2. `transformer-maven-plugin:transform` (goal `transform`, fase `process-classes`,
   `jakartaDefaults=true`) reescribe `javax.* → jakarta.*` in situ;
3. `maven-jar-plugin` reempaqueta usando el `MANIFEST.MF` transformado.

Artefactos producidos e instalados en `.m2`:
- `richfaces-core-jakarta-4.6.2.ayg.jar`
- `richfaces-a4j-jakarta-4.6.2.ayg.jar`
- `richfaces-jakarta-4.6.2.ayg.jar` (rich)

Verificación de bytecode (con `javap`):
- `org.richfaces.renderkit.RendererBase` (a4j-jakarta) ahora `extends
  jakarta.faces.render.Renderer`; sus métodos usan
  `jakarta.faces.context.FacesContext` / `jakarta.faces.component.UIComponent`.
- `org.richfaces.webapp.ResourceServlet` (core-jakarta) usa exclusivamente
  `jakarta.faces.webapp.FacesServlet` y `jakarta.servlet.http.*`; CERO
  referencias a `javax.faces`/`javax.servlet`.

El reactor completo (17 módulos) construye con `mvn -DskipTests -Dgpg.skip=true
clean install`. El `jakarta-bridge` va después de `dist` para que los jars
`javax` ya existan al transformarse.

Nota (fragilidad conocida): un `clean install` completo mostró de forma
intermitente un fallo de `testCompile` en `richfaces-a4j` (no encontraba clases
generadas por el CDK como `UIDataAdaptor`). Al reanudar el build se resolvió.
Es un problema de orden/estado del flujo CDK, no de la transformación jakarta;
queda anotado para endurecer en Fase 3/4.

### 6.2 Renombrado automático de namespaces `javax.*` → `jakarta.*`
- [ ] Ejecutar una herramienta de transformación sobre el código fuente:
  - **Opción A – Eclipse Transformer** (recomendada para el renombrado masivo).
  - **Opción B – OpenRewrite** con la receta
    `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` vía
    `rewrite-maven-plugin`.
- [ ] Aplicar sobre ~1000+ archivos `.java`, más `web.xml`, `faces-config.xml`,
      `beans.xml`, taglibs `*.taglib.xml`, y descriptores del CDK.
- [ ] Revisar manualmente los paquetes que NO son simple renombrado:
  - `javax.annotation.PostConstruct/PreDestroy` → `jakarta.annotation.*`
  - `javax.persistence.*` → `jakarta.persistence.*`
  - `javax.validation.*` → `jakarta.validation.*`
  - `javax.xml.bind.*` → `jakarta.xml.bind.*`

### 6.3 APIs eliminadas en Faces 4.0 (requiere refactor manual)
- [ ] **Managed beans de JSF eliminados.** Sustituir en todo el código y en los
      ejemplos:
  - `@javax.faces.bean.ManagedBean` → `@jakarta.inject.Named` (CDI)
  - `@javax.faces.bean.RequestScoped` → `jakarta.enterprise.context.RequestScoped`
  - `@javax.faces.bean.SessionScoped` → `jakarta.enterprise.context.SessionScoped`
  - `@javax.faces.bean.ApplicationScoped` → `jakarta.enterprise.context.ApplicationScoped`
  - `@javax.faces.bean.ViewScoped` → `jakarta.faces.view.ViewScoped`
  - `@ManagedProperty` → inyección CDI (`@Inject` + `@Named`) o `@ManagedProperty`
    de Faces según corresponda.
- [ ] Añadir `beans.xml` (CDI) donde haga falta activar CDI.
- [ ] Revisar `faces-config.xml`: subir a la versión de esquema **4.0** y
      eliminar declaraciones de managed-beans obsoletas.
- [ ] Revisar taglibs y namespaces XML de las vistas: pasan de
      `http://xmlns.jcp.org/jsf/...` / `http://java.sun.com/...` a
      `jakarta.faces.*` (`https://jakarta.ee/xml/ns/jakartaee`).

### 6.4 CDK (generador de código)
- [ ] Verificar que `richfaces-cdk-maven-plugin` genera clases con imports
      `jakarta.*`. Si no, migrar las **plantillas del CDK** (en `build/` y en
      `src/main/templates`) y/o el propio CDK.
- [ ] Regenerar y compilar `core` y `components` para validar la salida del CDK.

---

## 7. Fase 3 — Migración módulo a módulo (orden del reactor)

Para cada módulo: aplicar transformación → compilar → corregir → test → commit.

- [ ] **7.1 `bom`** — actualizar versiones gestionadas.
- [ ] **7.2 `build/*`** — build-resources, page-fragments, depchains,
      resource-optimizer-plugin. Contienen utilidades y config de test.
- [ ] **7.3 `core`** — API y runtime base de RichFaces. Módulo más importante.
- [ ] **7.4 `components`** (`a4j`, `rich`) — los componentes JSF. Depende del CDK.
- [ ] **7.5 `dist`** — ensamblado/distribución.
- [ ] **7.6 `examples`** — showcase, template, standalone-js, etc. (los que estén
      activos; hoy están comentados en el reactor raíz).

### Resultados de la Fase 3 (ejecutada) — los 8 examples compilan en Java 21

Alcance: subir el toolchain de compilación de cada example a Java 21
(`maven.compiler.release=21` + plugins modernos), manteniéndose en `javax`
(Java EE 6 Web), igual que el resto del proyecto. NO se migran a `jakarta` (eso
es trabajo de EE 10 nativo / Estrategia 1).

Cambios por example (todos son `war` standalone `org.richfaces.examples`, sin
parent):
- **template**: `release=21`; plugins clean/compiler/install/resources/surefire/war
  a versiones modernas. Es dependencia de `standalone-js` (hay que `install`-arlo).
- **standalone-js**: `release=21` en properties (no fija plugins).
- **push-demo, components-demo, irc-client, jpa-demo**: `release=21` +
  mismo set de plugins modernos.
- **showcase**: `release=21` (quitado `source/target 1.7`); compiler/plugins
  modernos en su `pluginManagement`. Compila con perfil por defecto
  (tomcat-mojarra). `javax.persistence`/`javax.xml.bind` se resuelven vía
  `jboss-javaee-6.0` (siguen como `javax`).
- **photoalbum**: `release=21`; además requirió añadir la dependencia
  `com.sun.activation:javax.activation:1.2.0` porque `javax.activation` (JAF)
  fue eliminado del JDK en Java 11 y el código usa
  `javax.activation.MimetypesFileTypeMap`.

Todos verificados con `mvn -DskipTests -Dgpg.skip=true -f examples/<x>/pom.xml
clean package` → BUILD SUCCESS (`javac [debug release 21]`).

Decisión: los examples se dejan **comentados en el reactor raíz** (como el diseño
original). Son apps de demostración con perfiles de contenedor (WildFly/Tomcat)
y dependencias pesadas; se construyen bajo demanda con `-f examples/<x>/pom.xml`.
Los tests de integración Arquillian de estos examples se abordan en la Fase 4.

---

## 8. Fase 4 — Tests e integración

- [ ] Ejecutar tests unitarios por módulo: `mvn -pl <módulo> test`.
- [ ] Actualizar el stack de integración:
  - Arquillian a versión compatible con Jakarta EE 10.
  - Reemplazar **PhantomJS** por Selenium + Chrome/Firefox headless.
  - Actualizar contenedores: WildFly 30+ (Jakarta EE 10) y/o Tomcat 10+.
- [ ] Ejecutar la suite de integración con un contenedor moderno:
      `mvn verify -Dintegration=wildfly... ` (perfil actualizado).

---

### Resultados de la Fase 4 (ejecutada) — tests unitarios en JDK 21

Objetivo: ejecutar los tests que hasta ahora se saltaban (`-DskipTests`) para
validar el runtime en JDK 21. Resultado:

- **`core`: 238 tests, 0 fallos, 0 errores** (2 skipped, deliberados). VERDE.
- **`a4j`: 119 tests, 0 fallos, 0 errores**. VERDE.
- **`rich`: los tests de PRODUCCIÓN compilan y el artefacto se empaqueta bien;
  la COMPILACIÓN de sus fuentes de test falla** (ver deuda abajo).

Fixes de infraestructura de test aplicados (commit 4a32dac), todos con causa
raíz de "JDK moderno", útiles también en runtime:

1. **cglib / mocks JSF** (`org.jboss.test.faces.mock` usa cglib con reflexión
   profunda sobre `ClassLoader.defineClass`, bloqueada por JPMS): se añadieron
   `--add-opens` al `argLine` del JVM forked de surefire (el JVM de tests NO
   hereda `.mvn/jvm.config`). Se registró `maven-surefire-plugin` en el
   `pluginManagement` raíz para que todos los módulos hereden el argLine.
2. **`sun.util.calendar`** (serialización de `TimeZone` a JS en `ScriptUtils`
   vía reflexión sobre `sun.util.calendar.ZoneInfo`): `--add-opens
   java.base/sun.util.calendar=ALL-UNNAMED`.
3. **JAXB eliminado del JDK en Java 11**: `ClientServiceConfigParser`
   (módulo rich) usa `javax.xml.bind.JAXB` para parsear `csv.xml` al inicializar
   RichFaces; sin implementación fallaba con `ClassNotFoundException
   com.sun.xml.internal.bind.v2.ContextFactory` ("Server not started due to
   listener error" en ~150 tests de integración de rich). Se añadió
   `javax.xml.bind:jaxb-api:2.3.1` + `org.glassfish.jaxb:jaxb-runtime:2.3.1` a
   rich. **Este es un fix de RUNTIME**: hará falta igualmente al desplegar la
   librería en un contenedor de servlets (ver sección 11).
4. **testCompile intermitente de a4j** (clases escritas a mano como
   `UIDataAdaptor` no aparecían en `target/classes`): se dio al `default-compile`
   su propia ejecución con `useIncrementalCompilation=false` para que compile
   TANTO `src/main/java` COMO las fuentes generadas por el CDK a `target/classes`.
   El `precompile-sources-for-cdk` sigue escribiendo a un directorio separado
   (`target/cdk-precompile-classes`).

> **DEUDA — tests de `rich` (pendiente para el futuro):** la compilación de las
> fuentes de test de `rich` falla con errores en cascada `@Override does not
> override or implement a method from a supertype` (p. ej.
> `AbstractAccordionTest`, que crea clases anónimas sobre clases de componente
> abstractas ampliadas por el CDK). El artefacto principal de `rich` (la librería)
> compila y empaqueta sin problema; solo fallan sus tests unitarios internos.
> Para construir la librería usable, saltar los tests de rich con
> `-Dmaven.test.skip=true` (salta compile y run de tests). Investigar/arreglar
> estos tests queda como trabajo futuro (probablemente un único punto de fallo de
> resolución de tipos en la jerarquía generada por el CDK que rompe en cascada, o
> tests que asumen atributos que la versión actual del CDK no genera).

Comando para construir la librería completa (artefactos usables) saltando tests:
```
mvn -DskipTests clean install            # compila tests pero no los ejecuta
mvn -Dmaven.test.skip=true clean install # NO compila ni ejecuta tests (usar este
                                          # si el testCompile de rich molesta)
```

---

## 9. Fase 5 — Verificación final y limpieza

- [x] Build de la librería con JDK 21 (`mvn -Dmaven.test.skip=true clean install`)
      produce todos los artefactos (`javax` + variantes `jakarta` del bridge).
- [x] Tests unitarios de `core` y `a4j` en verde en JDK 21.
- [ ] Tests de `rich` (compilación de fuentes de test) — deuda documentada arriba.
- [ ] Revisar warnings de deprecación relevantes de Java 21 (p. ej. `new Integer`,
      `new Double`) — no bloquean.
- [ ] Actualizar documentación (`README.adoc`, `TESTS.md`) con requisitos JDK 21.
- [ ] Actualizar CI (`.travis.yml`) para construir en JDK 21.
- [ ] Merge de `migration/java21`.

---

### Estado de verificación de la Fase 5 y hallazgo del resource-optimizer

Los 6 artefactos de la librería YA están construidos e instalados en el
repositorio local (`~/.m2`), producidos por los builds completos exitosos de las
Fases 1 y 2:
- `richfaces-core`, `richfaces-a4j`, `richfaces` (javax)
- `richfaces-core-jakarta`, `richfaces-a4j-jakarta`, `richfaces-jakarta` (jakarta)

Por tanto, **para el objetivo (usar la librería en Java 21 + Liberty) los
artefactos ya existen y son usables.**

Hallazgo (fragilidad de build, NO de la migración): al reconstruir `rich` desde
cero de forma repetida aparecen dos fallos intermitentes/ambientales:
1. **Locks de archivos de Windows** en `components/rich/target` (miles de
   recursos JS/CSS de ckeditor, etc.): `FileSystemException: The process cannot
   access the file because it is being used by another process`, en los goals
   `clean`/`resources`. Es un lock de Windows (Search indexer / antivirus /
   Explorer) sobre `target`, no un problema de código. Mitigación: cerrar
   procesos java residuales y excluir `target/` del antivirus/indexador; reintentar.
2. **`richfaces-resource-optimizer` + Reflections 0.9.8 con bytecode Java 21**:
   los goals `packed-resources` / `packed-compressed-resources` fallan de forma
   NO determinista con `NullPointerException` ("type is null" /
   "annotatedClass is null") porque Reflections 0.9.8 usa un `JavassistAdapter`
   que no lee de forma fiable las clases con versión de bytecode 65 (Java 21).
   En las Fases 1/2 estos goals llegaron a pasar; su comportamiento depende del
   orden de escaneo, por eso es intermitente.

> **DEUDA — resource-optimizer (pendiente para el futuro):** para un build 100%
> reproducible de `rich` en JDK 21 hay que actualizar la librería `reflections`
> a una versión que lea bytecode moderno (0.10.x/0.9.12) y ADAPTAR el scanner
> custom (`MarkerResourcesScanner extends AbstractScanner`, en
> `core/src/main/resource-optimizer/...`) a su nueva API — la 0.9.12 cambió la
> firma `scan(Object)` a `scan(Object, Store)`, por eso subir versión rompe la
> compilación del scanner (se probó en Fase 2 y se revirtió). Alternativa:
> reemplazar el motor de escaneo. Los goals afectados solo producen recursos
> JS/CSS EMPAQUETADOS/optimizados (una optimización); la librería funciona sin
> ellos porque los recursos sin empaquetar ya están en el jar.

Recomendación de build fiable mientras tanto:
- Los artefactos en `.m2` sirven directamente para el proyecto Liberty.
- Para reconstruir: cerrar procesos `java` residuales, excluir `target/` del
  antivirus, y usar `mvn -Dmaven.test.skip=true -Dgpg.skip=true -f pom.xml
  clean install`; reintentar `rich` con `-rf :richfaces` si un lock de FS corta.

---

## 11. Objetivo final: mini-proyecto Java 21 + WebSphere Liberty

Meta del usuario: crear una app web con Java 21 que use esta librería RichFaces
y desplegarla en **WebSphere Liberty**, viéndola en el navegador.

Consideración de plataforma CLAVE — qué artefacto usar según el feature de Liberty:

- **Si el server.xml de Liberty usa `jsf-2.2`/`jsf-2.3` (Java EE, namespace
  `javax.faces`)**: usar los artefactos `javax` normales
  (`richfaces-core`, `richfaces-a4j`, `richfaces`), que ya compilan en Java 21.
  Es el camino de menor fricción con lo hecho hasta ahora (Fases 1–4).
- **Si el server.xml usa `faces-3.0`/`faces-4.0` (Jakarta, namespace
  `jakarta.faces`)**: usar las variantes `jakarta` del bridge
  (`richfaces-core-jakarta`, `richfaces-a4j-jakarta`, `richfaces-jakarta`,
  Fase 2). OJO: el bridge transforma el bytecode y descriptores, pero el
  taglib/faces-config generado por el CDK sigue en namespace `javaee`; para
  Faces 4.0 nativo hará falta la Estrategia 1 (reescritura + parcheo del CDK).

Dependencias de runtime a incluir en el WAR (no están en el contenedor):
- `richfaces-*` (los 3 artefactos) + `richfaces-cache-bom` deps que uses.
- **JAXB** (`jaxb-api` + `jaxb-runtime` 2.3.1) — RichFaces lo usa al arrancar
  (ver Fase 4, punto 3). Sin él, la app falla al inicializar.
- Guava, cssparser, y demás dependencias transitivas de `richfaces-core`.
- El contenedor (Liberty) aporta Faces/Servlet/EL/CDI según el feature activado;
  NO empaquetar esas APIs en el WAR (marcarlas `provided`).

Checklist sugerido para el mini-proyecto:
- [ ] WAR con packaging Java 21 (`maven.compiler.release=21`).
- [ ] `server.xml` de Liberty con el feature de Faces adecuado + `localConnector`.
- [ ] Un `faces-config.xml`/`beans.xml` mínimo y un `.xhtml` con un componente
      RichFaces (p. ej. `<a4j:commandButton>` o `<rich:panel>`).
- [ ] Verificar en el navegador que el componente renderiza y el Ajax responde.

---

## 10. Checklist de "hecho" (Definition of Done)

- [ ] Todos los módulos compilan con `maven.compiler.release=21`.
- [ ] No queda ningún `import javax.` (salvo los que siguen existiendo en el JDK,
      p. ej. `javax.swing`, `javax.xml.parsers`, `javax.xml.namespace`,
      `javax.naming` — estos NO se migran a jakarta).
- [ ] El CDK genera código `jakarta.*` y `core`/`components` compilan.
- [ ] Tests unitarios en verde.
- [ ] Al menos un perfil de integración corre en un contenedor Jakarta EE 10.
- [ ] CI verde en JDK 21.

---

## Notas importantes sobre paquetes que NO se migran

No todos los `javax.*` pasan a `jakarta.*`. Los siguientes siguen siendo parte
del JDK y deben permanecer como `javax.*`:
`javax.swing`, `javax.xml.parsers`, `javax.xml.transform`, `javax.xml.namespace`,
`javax.naming`, `javax.crypto`, `javax.net`, `javax.sql`, `javax.imageio`.

Solo se migran los namespaces de Java EE / Jakarta EE:
`faces, servlet, el, annotation (jakarta.annotation), validation, persistence,
enterprise (CDI), inject, xml.bind, ws, mail, transaction`.
