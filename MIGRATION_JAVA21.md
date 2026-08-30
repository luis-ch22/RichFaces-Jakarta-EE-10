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

## 9. Fase 5 — Verificación final y limpieza

- [ ] Build completo limpio con JDK 21: `mvn clean install`.
- [ ] Revisar warnings de deprecación relevantes de Java 21.
- [ ] Actualizar documentación (`README.adoc`, `TESTS.md`) con requisitos JDK 21.
- [ ] Actualizar CI para construir y testear en JDK 21.
- [ ] Revisión de código de los refactors manuales (CDI, faces-config, taglibs).
- [ ] Merge de `migration/java21`.

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
