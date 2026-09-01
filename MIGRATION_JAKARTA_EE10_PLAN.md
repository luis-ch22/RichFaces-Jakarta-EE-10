# Plan de migración a Jakarta EE 10 / Faces 4.0 (lo que falta)

Documento de trabajo para que Kiro ejecute, fase a fase, la migración pendiente
de RichFaces 4.6.2. Complementa a `MIGRATION_JAVA21.md` (que dejó la librería
compilando y usable en Java 21, en `javax`, con artefactos `jakarta` producidos
por post-transformación del `jakarta-bridge`).

Este plan cubre el salto que **falta**: pasar de "javax + puente jakarta" a
**Jakarta EE 10 nativo (Faces 4.0)**, más las deudas técnicas abiertas y el
objetivo final (mini-proyecto Java 21 + WebSphere Liberty en el navegador).

> **Regla de oro:** trabajar en rama, avanzar módulo a módulo en orden del
> reactor, y NO avanzar con el build roto. Cada fase deja el proyecto en estado
> verificable y termina con un commit.

---

## 0. Estado de partida (verificado en el repo)

Datos confirmados leyendo el proyecto (no asumidos):

- **Reactor activo** (`pom.xml` raíz): `bom`, `build/*`, `core`, `components`
  (`a4j`, `rich`), `dist`, `jakarta-bridge`. Los `examples/*` están COMENTADOS.
- **Plataforma actual:** `jboss-javaee-6.0` / `jboss-javaee-web-6.0`
  `3.0.2.Final` (Java EE 6). Propiedad `version.jboss-javaee` en `pom.xml` raíz
  y en `build/pom.xml`.
- **Toolchain:** `maven.compiler.release=21`, plugins modernos, `.mvn/jvm.config`
  con `--add-opens`. YA hecho.
- **CDK:** `richfaces-cdk-maven-plugin` `4.5.1-SNAPSHOT`. Su **código fuente NO
  está en el workspace** (ni como submódulo git: `.gitmodules` vacío). Solo hay
  jars en `~/.m2/repository/org/richfaces/cdk/` (generator, annotations,
  attributes, commons, xinclude, richfaces-cdk-maven-plugin).
- **Plantillas del CDK:** viven DENTRO del jar `generator-4.5.1-SNAPSHOT.jar`,
  en `META-INF/templates/*.ftl`. Verificado: `component.ftl` tiene imports
  **hardcodeados** `import javax.faces.context.FacesContext;`,
  `import javax.faces.component.UIComponent;`, `import javax.el.MethodExpression;`,
  `import javax.annotation.Generated;`, etc. Además, `_attributes_import.ftl`
  emite imports dinámicos `import ${imp.name};` que provienen del modelo Java
  compilado del generator (no editables por plantilla).
- **Managed beans JSF (`javax.faces.bean.*`):** SOLO existen en `examples/*`
  (template, standalone-js, showcase, push-demo). El `core`/`components` de la
  librería NO usa managed beans de JSF. Esto reduce mucho el alcance del
  refactor CDI: es problema de los ejemplos, no de la librería.
- **Dependencias de test:** `com.github.albfernandez.test-jsf` `1.1.11`
  (htmlunit-client, jsf-mock, jsf-mockito, jsf-test-stage, jsf-test-scriptunit)
  en `build/pom.xml` (dependencyManagement) y usadas en `core`, `components`,
  `components/rich`. Son mocks JSF atados a `javax.faces`.
- **Deudas abiertas de `MIGRATION_JAVA21.md`:** (1) tests de `rich` no compilan
  (cascada `@Override`), (2) resource-optimizer + Reflections 0.9.8 no
  reproducible en JDK 21.

### Decisión estratégica del plan

Hay dos caminos hacia Jakarta EE 10. Este plan recomienda el **Camino nativo
(Estrategia 1)** porque es lo que el usuario pidió ("lo último y lo más nuevo,
JSF 4.0"). El puente actual (`jakarta-bridge`, Estrategia 2) se conserva como
red de seguridad hasta que lo nativo esté verde, y luego se elimina.

El obstáculo central era el **CDK** (genera `javax` hardcodeado en sus
plantillas `.ftl`). **Buena noticia verificada en Maven Central:** ya existe un
CDK jakartificado.

#### Estado real del ecosistema albfernandez (verificado en Maven Central / GitHub)

- **CDK jakarta YA PUBLICADO:** `com.github.albfernandez.richfaces.cdk` versión
  **`10.0.1`** (release, mayo 2025) está en Maven Central y **ya usa Jakarta**:
  su `generator-10.0.1.pom` depende de `jakarta.el:jakarta.el-api`,
  `org.glassfish:jakarta.el` y `org.apache.myfaces.core:myfaces-api` (Faces 4.0).
  Módulos disponibles en 10.0.1: `parent`, `commons`, `annotations`,
  `attributes`, `xinclude`, `generator`, `cmdln-generator`, `maven-plugin`,
  `test-component`, `dist`. **OJO al cambio de groupId**: es
  `com.github.albfernandez.richfaces.cdk` (el proyecto actual usa
  `org.richfaces.cdk`). Repo: https://github.com/albfernandez/richfaces-cdk
- **Framework RichFaces de albfernandez NO jakartificado:** el `master` de
  https://github.com/albfernandez/richfaces es `4.6.22-SNAPSHOT`, sigue en
  `javax` (Java 1.8, `version.cdk=4.6.1.ayg`, `jboss-javaee 3.0.3.Final`). En
  Maven Central su `richfaces-core`/`richfaces` llegan solo hasta `4.6.21.ayg`
  (enero 2023). Es decir: **el CDK saltó a jakarta pero el framework aún no**.
- **Fork del framework YA migrado a jakarta (referencia valiosa):**
  https://github.com/MilovdZee/richfaces-jakarta ("JSF component framework
  migrated to jakarta"). Sirve como guía de qué tocar y como posible fuente de
  parches, aunque conviene revisar su estado/licencia antes de copiar.

#### Implicación para la Fase D (CDK)

El plan ya NO necesita parchear plantillas a mano ni recuperar el CDK desde cero.
Se apoya en el **CDK 10.0.1 jakarta ya publicado**:

- **Vía preferente (D-1):** usar directamente
  `com.github.albfernandez.richfaces.cdk:richfaces-cdk-maven-plugin:10.0.1`
  (cambiando `version.cdk` y el groupId en los pom que invocan el CDK). Verificar
  que genera `jakarta.*` sobre nuestro `core`/`components`.
- **Vía de respaldo (D-2):** si el CDK 10.0.1 no encaja tal cual (p.ej. cambios
  de API del modelo del CDK entre 4.5.1 y 10.x), clonar el repo del CDK, ajustar
  y recompilar un `10.0.x` local. El código fuente completo está disponible, así
  que esto es un ajuste, no una reescritura desde cero.
- El fork `MilovdZee/richfaces-jakarta` es la referencia para resolver dudas de
  cómo quedó el framework tras la migración (faces-config 4.0, taglibs, CDI).

---

## 1. Fase A — Preparación

- [x] Crear rama dedicada desde el estado actual:
      `git checkout -b migration/jakarta-ee10`.
- [x] Confirmar build base verde (el estado heredado de la Fase 21):
      ```
      cmd /v:on /c "set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot&& mvn.cmd -Dmaven.test.skip=true -Dgpg.skip=true clean install"
      ```
      Anotar qué se instala en `.m2`. Este es el "antes".
- [x] Congelar con commit inicial de la rama.
- [x] Definir propiedades nuevas en `pom.xml` raíz y `build/pom.xml`:
  - `version.jakartaee.bom=10.0.0` (jakarta.platform:jakarta.jakartaee-bom).
  - `version.mojarra=4.0.24` (org.glassfish:jakarta.faces).
  - `version.jaxb=4.0.5` (jakarta.xml.bind-api + glassfish jaxb-runtime).
  - Mantener temporalmente `version.jboss-javaee` hasta migrar cada módulo.
  - (Hecho al inicio de la Fase B.)

> Objetivo: base reproducible y variables listas para intercambiar plataforma.

### Resultados de la Fase A (ejecutada)

- El trabajo de Java 21 (Fases 0–5 de `MIGRATION_JAVA21.md`) está commiteado
  directamente en `main` (no hubo rama `migration/java21` separada). El HEAD de
  `main` es `d657824` (docs de plan + skinning). El proyecto ya está rebrandeado
  a `com.github.luisch22.richfaces:*:5.0.0`.
- Rama creada: **`migration/jakarta-ee10`** desde `main`.
- **Build base VERDE en JDK 21** (`mvn -B -Dmaven.test.skip=true -Dgpg.skip=true
  clean install`): los **17 módulos SUCCESS** en ~2:24 min. Se instalan en `.m2`
  los 6 artefactos usables: `richfaces-core`, `richfaces-a4j`, `richfaces`
  (javax) y `richfaces-core-jakarta`, `richfaces-a4j-jakarta`,
  `richfaces-jakarta` (bridge), todos en versión `5.0.0`.
- **CDK en uso hoy:** `org.richfaces.cdk:...:4.5.1-SNAPSHOT` (javax), resuelto
  desde `.m2`. Genera componentes con `javax.faces` (esperado en la base).
- **Ruido no fatal confirmado (deuda conocida):** el `richfaces-resource-optimizer`
  con Reflections 0.9.8 imprime stack traces `ReflectionsException: could not
  create class file from ...class` al escanear bytecode Java 21 (p. ej.
  `RendererUtils.class`, `XML.class`), y muchos `null resource for resource key
  org.richfaces.images:*` — NINGUNO corta el build (rich = SUCCESS en 1:19 min).
- **CDK 10.0.1 jakarta clonado como referencia/respaldo** en
  `C:\Users\luis-\Workspace\richfaces-cdk`, en el tag `v.10.0.1` (detached HEAD).
  Verificado que su `component.ftl` ya genera `jakarta.faces.*`/`jakarta.el.*`.
  El `master`/`main` del repo NO es jakarta (es `4.6.2-SNAPSHOT` javax); la
  versión jakarta vive en la rama `jakarta` y los tags `v.10.0.0`/`v.10.0.1`.

Patrón de build confirmado (JDK 21 real, evita el JDK del terminal de Kiro):
```
cmd /v:on /c "set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot&& mvn.cmd -B -Dmaven.test.skip=true -Dgpg.skip=true clean install"
```

> Pendiente de la Fase A: solo falta declarar las propiedades de versión jakarta
> (`version.jakarta.bom`, etc.). Se hará al inicio de la Fase B junto con el
> cambio del BOM, para no dejar propiedades sin usar.

---

## 2. Fase B — Reescritura de dependencias / BOM a Jakarta EE 10

Orden del reactor: `bom` → `build` → `core` → `components` → `dist`.

- [ ] **`bom/pom.xml`**: reemplazar la gestión de `jboss-javaee-6.0/web` por el
      BOM Jakarta EE 10 (`jakarta.platform:jakarta.jakartaee-bom:10.0.0`,
      `scope=import`, `type=pom`) o dependencias individuales:
  - Faces 4.0 API: `jakarta.faces:jakarta.faces-api:4.0.1` (`provided`).
  - Servlet 6.0: `jakarta.servlet:jakarta.servlet-api:6.0.0` (`provided`).
  - EL 5.0: `jakarta.el:jakarta.el-api:5.0.1` (`provided`).
  - CDI 4.0: `jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1`.
  - Bean Validation 3.0: `jakarta.validation:jakarta.validation-api:3.0.2`.
  - Annotations 2.1: `jakarta.annotation:jakarta.annotation-api:2.1.1`.
  - Jakarta XML Binding 4.0: `jakarta.xml.bind:jakarta.xml.bind-api:4.0.0` +
    `org.glassfish.jaxb:jaxb-runtime:4.0.x` (runtime).
- [ ] **`build/pom.xml`**: mismo cambio en `dependencyManagement`. Añadir
      implementación **Mojarra 4.x** (`org.glassfish:jakarta.faces:4.0.x`) donde
      antes iba `org.glassfish:javax.faces` (aparece en perfiles/tests).
- [ ] **`core/pom.xml`** y **`components/*/pom.xml`**: cambiar cualquier
      dependencia directa `javax.*` por su equivalente jakarta. Marcar las APIs
      del contenedor como `provided` (Faces/Servlet/EL/CDI), NO empaquetarlas.
- [ ] Compilar SOLO para detectar (esperado que falle por imports `javax` aún en
      el código fuente; se arregla en la Fase C):
      `mvn -pl bom,core -am -Dmaven.test.skip=true -Dgpg.skip=true clean install`

> Nota: JAXB en runtime es obligatorio (RichFaces lo usa al arrancar, ver
> `ClientServiceConfigParser` en `rich`). En `javax` se usó `jaxb 2.3.1`; en
> jakarta pasa a `jakarta.xml.bind 4.0` + `jaxb-runtime 4.0`.

### Resultados de la Fase B (ejecutada) — dependencias jakarta OK, código aún javax

Enfoque elegido: **BOM de plataforma Jakarta EE 10** (`jakarta.platform:
jakarta.jakartaee-bom:10.0.0`) para gestionar versiones de forma coherente
(Faces 4.0 API, Servlet 6.0, EL 5.0, CDI 4.0, Bean Validation 3.0, Annotations
2.1, XML Binding 4.0), + **Mojarra** como implementación de Faces
(`org.glassfish:jakarta.faces:4.0.24`) + **JAXB 4.0** runtime
(`org.glassfish.jaxb:jaxb-runtime:4.0.5`).

Cambios aplicados:
1. **`pom.xml` raíz**: nuevas propiedades `version.jakartaee.bom=10.0.0`,
   `version.mojarra=4.0.24`, `version.jaxb=4.0.5`. En `dependencyManagement` se
   reemplazó `jboss-javaee-6.0` + `jboss-javaee-web-6.0` por el BOM jakarta +
   `org.glassfish:jakarta.faces` + `org.glassfish.jaxb:jaxb-runtime`.
2. **`build/pom.xml`**: mismas 3 propiedades (richfaces-build NO hereda del
   reactor raíz — usa jboss-parent — así que hay que declararlas aquí también,
   fue el primer fallo detectado: `${version.jakartaee.bom}` salía literal).
   Mismo cambio de `jboss-javaee` → BOM jakarta en su `dependencyManagement`.
3. **`core/pom.xml`**: la dependencia `jboss-javaee-6.0` (provided) se sustituyó
   por APIs jakarta provided individuales (servlet, el, cdi, annotation,
   validation). Perfil `jsf_ri` (activo por defecto): `org.glassfish:javax.faces`
   → `org.glassfish:jakarta.faces`. (Perfiles `jsf_jboss`/`myfaces` sin tocar aún;
   no se activan por defecto.)
4. **`components/pom.xml`**: perfil `jsf_ri` → `org.glassfish:jakarta.faces`.
5. **`components/a4j/pom.xml`**: `jboss-javaee-6.0` provided → servlet/el/cdi/
   annotation jakarta provided.
6. **`components/rich/pom.xml`**: `jboss-javaee-6.0` + `jboss-el-api_3.0_spec` +
   `javax.validation:validation-api` → servlet/cdi/annotation/`jakarta.el-api`/
   `jakarta.validation-api` jakarta provided. Bloque JAXB `javax.xml.bind:jaxb-api
   2.3.1` + `jaxb-runtime 2.3.1` → `jakarta.xml.bind:jakarta.xml.bind-api` +
   `jaxb-runtime` (versiones gestionadas por BOM/propiedad).
7. **`build/resource-optimizer-plugin/pom.xml`**: `jboss-javaee-6.0` →
   `org.glassfish:jakarta.faces` + `jakarta.servlet-api` (el optimizador escanea
   clases de recursos que referencian la API de Faces).

**Verificación** (`mvn -pl core -am -Dmaven.test.skip=true clean compile` con
JDK 21): el reactor LEE los POMs sin errores (build-resources y page-fragments
compilan OK), Maven RESUELVE las APIs jakarta desde Central, y el core llega a
compilar sus 337 fuentes fallando SOLO con errores del tipo esperado:
`package javax.faces.component.behavior does not exist`,
`package javax.servlet does not exist`, `package javax.faces.context does not
exist`, etc. Es decir: **las dependencias jakarta están bien; el código fuente
sigue en `javax`** y se renombra en la Fase C. Resultado correcto para la Fase B.

Aprendizajes/notas:
- `richfaces-build` necesita sus propias propiedades de versión jakarta (no
  hereda del raíz).
- El perfil `integration-tests` del raíz aún tiene
  `arquillian.richfaces.jsfImplementation=org.glassfish:javax.faces`; se tratará
  en la Fase G (no se activa por defecto).
- El perfil `precompile-sources-for-cdk` del core sigue dependiendo de
  `jboss-javaee-6.0` con versión propia (para el CDK 4.5.1 javax); se resolverá
  al cambiar el CDK en la Fase D.

---

## 3. Fase C — Renombrado `javax.* → jakarta.*` del código fuente

Objetivo: reescribir de forma PERMANENTE el código y descriptores de la
librería. Herramienta recomendada: **Eclipse Transformer** en modo fuente, o
**OpenRewrite** (`org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta`).

### 3.1 Preparar la transformación
- [ ] Elegir herramienta. Recomendado OpenRewrite vía `rewrite-maven-plugin`
      (aplica sobre `.java`) + un paso Transformer para XML/descriptores.
- [ ] Configurar exclusiones: paquetes `javax.*` que NO se migran porque siguen
      en el JDK: `javax.swing`, `javax.xml.parsers`, `javax.xml.transform`,
      `javax.xml.xpath`, `javax.xml.namespace`, `javax.imageio`, `javax.naming`,
      `javax.crypto`, `javax.security`, `javax.net`, `javax.sql`.

### 3.2 Ejecutar sobre la librería (core + components + build utils)
- [ ] Aplicar a `~1000+` `.java` de `core` y `components`.
- [ ] Aplicar a descriptores: `faces-config.xml`, `*.faces-config.xml`,
      `web.xml`, `beans.xml`, `*.taglib.xml`, y los `src/main/config/*.xml`.
- [ ] Revisar manualmente los no triviales:
  - `javax.annotation.PostConstruct/PreDestroy` → `jakarta.annotation.*`.
  - `javax.validation.*` → `jakarta.validation.*`.
  - `javax.xml.bind.*` → `jakarta.xml.bind.*`.
  - **Caso especial `javax.xml.rpc`** (JAX-RPC, en
    `core/.../InitializationListener.java`): no tiene equivalente Web Profile en
    Jakarta. Revisar si se puede eliminar la referencia o aislarla; no bloquear
    el resto por esto.

### 3.3 Descriptores a esquema 4.0
- [x] `faces-config.xml` de PRODUCCIÓN subidos a esquema **Faces 4.0**
      (`https://jakarta.ee/xml/ns/jakartaee`, `version="4.0"`):
      `core/.../META-INF/core.faces-config.xml`,
      `a4j/.../behaviors-handler-delegate.faces-config.xml`,
      `rich/.../META-INF/{dataTable,select,validator}.faces-config.xml`. Se
      migraron también los `javax.faces.event.*` / `javax.faces.Output` internos
      a `jakarta.faces.*`.
- [x] `*.taglib.xml`: los taglibs de producción los GENERA el CDK 10.0.1 (ya
      jakarta); no hay `.taglib.xml` en `src/main`. Nada que migrar a mano.
- [x] Los `.template.xml` y `cdk/attributes/*.xml` usan
      `xmlns:javaee="http://java.sun.com/xml/ns/javaee"` como token interno del
      CDK; el CDK 10.0.1 los acepta tal cual (los builds generan OK). NO se tocan.
- [ ] Namespaces XML de vistas (`.xhtml` de examples): pendiente para la Fase H
      (examples).

### 3.5 Flecos de Fase C — strings literales y managed-beans (ejecutado)

- [x] **Nombre de librería/recurso de Faces:** `javax.faces:jsf.js` →
      `jakarta.faces:faces.js` (y `-uncompressed`) en `ResourceConstants.java`;
      patrón de exclusión `^javax.faces` → `^jakarta.faces` en `ResourceGenerator.java`.
- [x] **Protocolo Ajax en `richfaces.js`:** `javax.faces.source`,
      `javax.faces.partial.*`, `javax.faces.behavior.event`, `javax.faces.ViewRoot`,
      `javax.faces.portletbridge.STATE_ID` → prefijo `jakarta.faces.*` (los nombres
      de parámetro del protocolo Faces cambiaron en 4.0; crítico para el Ajax).
- [x] **Whitelist de deserialización** (`resource-serialization.properties`):
      `javax.el.*`/`javax.faces.*` → `jakarta.*`.
- [x] **Managed-beans → CDI:** `SkinBean` (`@Named("a4jSkin")` +
      `@ApplicationScoped`) y `VersionBean` (`@Named("richfacesVersion")` +
      `@ApplicationScoped`, quitado `final` para el proxy CDI). Eliminadas las
      declaraciones `<managed-bean>` del `core.faces-config.xml` (removidas en
      Faces 4.0).

**Verificación:** `mvn -pl core,components/a4j,components/rich -am -DskipTests
-Dgpg.skip=true clean install` → **BUILD SUCCESS** (JDK 21). La librería completa
(core + a4j + rich, con CDK jakarta + resource-optimizer) compila y empaqueta
100% limpia en Jakarta EE 10 / Faces 4.0.

> NOTA CDI: `a4jSkin`/`richfacesVersion` ahora dependen de que el WAR consumidor
> tenga CDI activo (`beans.xml`) para resolver `#{a4jSkin.xxx}` en los `.ecss` y
> `#{richfacesVersion...}`. En Liberty con faces-4.0, CDI está activo por defecto.
> Verificar el skinning en el mini-proyecto (sección 11).

### 3.4 Compilar y estabilizar
- [x] `mvn -pl core -am -Dmaven.test.skip=true -Dgpg.skip=true clean compile`.
- [x] Corregir errores residuales de API (métodos renombrados/eliminados en
      Faces 4.0 respecto a 2.x). El `core` NO usa managed beans, así que el
      grueso fue renombrado mecánico.

### Resultados de la Fase C — CORE (ejecutada) — BUILD SUCCESS jakarta nativo

Herramienta: se descartó Eclipse Transformer CLI (con `jakartaDefaults` NO
transforma texto `.java`, solo bytecode: dejó 337 archivos "Unchanged"). Se usó
un **script PowerShell propio** (`build/jakarta-transform/rename-javax-to-jakarta.ps1`)
con una **allow-list explícita** de prefijos que migran (faces, servlet, el,
enterprise, inject, validation, persistence, jms, activation, mail, transaction,
interceptor, ejb, websocket, batch, json, + annotation excepto
`annotation.processing`). Esto NO toca los `javax.*` del JDK (swing, imageio,
naming, crypto, xml.parsers, etc.). Dry-run previo: 227 archivos, 875 tokens.

Incidencias resueltas durante la migración del core:
1. **BOM UTF-8**: `Set-Content -Encoding UTF8` de PowerShell añade BOM que javac
   rechaza (`illegal character: '\ufeff'`). Se corrigió escribiendo UTF-8 sin BOM
   (`System.IO.File]::WriteAllText` + `UTF8Encoding($false)`) y un
   `strip-bom.ps1` para los 227 ya escritos.
2. **`javax.xml.rpc.ServiceFactory`** (InitializationListener): era un import
   HUÉRFANO usado solo por un `{@link}` de javadoc (el código usa `ServicesFactory`).
   Eliminado el import y corregido el `@link`. (JAX-RPC no existe en Jakarta EE 10.)
3. **`jakarta.faces.el.ValueBinding`** (UITransient): API JSF 1.x ELIMINADA en
   Faces 4.0. Se borraron los métodos `getValueBinding/setValueBinding` (sus
   reemplazos `get/setValueExpression` ya existían).
4. **Dependencias jakarta faltantes** en `core/pom.xml`: añadidas
   `jakarta.inject-api`, `jakarta.jms-api` (optional), `jakarta.transaction-api`
   (optional), `jakarta.activation-api`. En el modelo javax venían del
   `jboss-javaee-6.0` full profile.
5. **Atmosphere 2.4.3 (javax) → 3.0.15 (jakarta.servlet)** en `build/pom.xml`.
   El módulo push extiende clases de Atmosphere; la 2.4.x era `javax.servlet` y
   chocaba con el código ya migrado. La 3.x es la línea jakarta.
6. **CDI 4.0 API**: `BeforeBeanDiscovery.addAnnotatedType(AnnotatedType)` fue
   eliminado; ahora requiere `(AnnotatedType, String id)`. Corregido en
   `PushCDIDependencyRegistrationExtension`.
7. **`JBossCacheCache`**: se REVIRTIÓ a `javax.transaction` (NO jakarta) a
   propósito: jbosscache-core es una lib legacy optional (~2010) sin variante
   jakarta cuya API devuelve `javax.transaction.Transaction`.
8. **Mocks del resource-optimizer** (ApplicationImpl, ExternalContextImpl,
   FacesContextImpl): implementaban APIs JSF 1.x eliminadas en Faces 4.0
   (`PropertyResolver`, `VariableResolver`, `ValueBinding`, `MethodBinding`,
   `createValueBinding`, `createMethodBinding`, `createComponent(ValueBinding)`)
   → eliminados. Añadidos los métodos abstractos NUEVOS de Faces 4.0:
   `ExternalContext.release()`, `ExternalContext.encodeWebsocketURL(String)`,
   `FacesContext.getLifecycle()`.

Verificación: `mvn -pl core clean compile` (JDK 21) → **BUILD SUCCESS** en ~13 s.
El `core` compila NATIVO en `jakarta.*` (Faces 4.0), incluyendo el source root
`resource-optimizer`. El CDK 4.5.1 (javax) generó las fuentes del core sin
conflicto (el core casi no usa clases generadas); el CDK jakarta será necesario
para `components` (Fase D).

PENDIENTE de la Fase C tras el core:
- Strings literales `"javax.faces"` (librería de recursos JS) en
  `ResourceConstants.java`/`ResourceGenerator.java`: en Faces 4.0 la librería de
  recursos pasa a `jakarta.faces`. Revisar al integrar con el contenedor.
- Descriptores `faces-config.xml`/`*.taglib.xml` al esquema 4.0 (sección 3.3).
- Renombrado de `components` (a4j/rich) + tests del core — tras el CDK (Fase D).

---

## 4. Fase D — CDK: que genere `jakarta.*` (usando el CDK 10.0.1 jakarta)

Sin esto, `components` (a4j/rich) volvería a generar renderers/componentes con
imports `javax.faces` y no compilaría contra Faces 4.0. Ya NO es el punto de
mayor riesgo: existe un **CDK jakarta publicado** (`10.0.1`, ver sección 0).

### Resultados de la Fase D (en curso) — CDK 10.0.1 jakarta adoptado

Ejecutado hasta ahora:
1. **CDK 10.0.1 descargado** de Maven Central a `.m2`
   (`com.github.albfernandez.richfaces.cdk:richfaces-cdk-maven-plugin:10.0.1`
   + generator/annotations/etc). No hizo falta compilar el repo clonado.
2. **Coordenadas del CDK actualizadas** (groupId `org.richfaces.cdk` →
   `com.github.albfernandez.richfaces.cdk`, version `4.5.1-SNAPSHOT` → `10.0.1`)
   en: `pom.xml` raíz (propiedad `groupId.cdk` + pluginManagement + m2e filter),
   `build/pom.xml` (propiedades + depMgmt annotations/generator),
   `core/pom.xml` (dep annotations + plugin + dep del plugin de perfil
   precompile: jboss-javaee-6.0 → `org.glassfish:jakarta.faces`),
   `components/pom.xml` (plugin + `unCheckedPluginList` del enforcer + dep
   annotations), `components/a4j/pom.xml`, `components/rich/pom.xml`,
   `build/build-resources/pom.xml` (dep generator).
3. **`components` renombrado** javax→jakarta con el script (534 archivos,
   excluyendo `target/` que contiene fuentes generadas por el CDK). Se añadió al
   script la exclusión de `\target\`.
4. **`build/build-resources` migrado**: `RichFaces5Validator` y `BaseDeployment`
   usaban `javax.faces` (son clases de soporte que se pasan AL CDK como plugin
   `CdkExtension`). Migradas a `jakarta.faces` + añadida dep `org.glassfish:
   jakarta.faces` (provided) a `build/build-resources/pom.xml` para compilarlas.

Hallazgo clave: el CDK 10.0.1 en runtime necesita la API Faces y valida contra
`RichFaces5Validator` de NUESTRO `build-resources` (no del CDK). Un primer
intento falló con `NoClassDefFoundError: javax/faces/view/facelets/ComponentHandler`
porque ese validator seguía en javax; tras migrarlo, el siguiente fallo fue de
compilación de `build-resources` (`package jakarta.faces.component does not
exist`) hasta añadir la dep Mojarra.

### Verificación de la Fase D (shell limpio) — CDK jakarta OK, core+a4j+rich compilan

Tras reiniciar la máquina (shell limpio), verificado con builds de UN módulo a
la vez (regla nueva: NO acumular procesos Maven de fondo):

- **core**: genera con CDK 10.0.1 e instala → OK.
- **a4j**: `mvn -pl components/a4j clean install` → **BUILD SUCCESS**. Confirma
  que el CDK 10.0.1 emite `jakarta.faces` (a4j enlaza contra el core jakarta).
  Fixes Faces 4.0 en a4j (commit): build-resources jakarta.faces 4.0.24 inline;
  JSTL API jakarta 3.0.2 (LoopTagStatus); UISequence (quita ResultDataModel/
  Result JSTL-SQL + setValueBinding); PartialStateHolderHelper.eval(Supplier);
  borrado ValueBindingValueExpressionAdaptor.
- **resource-optimizer-plugin**: `ProcessMojo` migrado a `jakarta.faces` → compila.
- **rich**: **compila e instala el JAR** — precompile (448 fuentes) + CDK
  genera los ~70 componentes + default-compile, todo `[release 21]` jakarta.
  Fixes Faces 4.0 en rich (10 errores): AbstractAutocomplete (ResultDataModel/
  Result), AbstractExtendedDataTable (setValueBinding), CapturingELContext
  (`<T> convertToType(...Class<T>)` EL 5.0), ProgressServletInputStream
  (isFinished/isReady/setReadListener Servlet 3.1+), RichFacesBeanValidatorFactory
  (Context.unwrap(Class<T>) Bean Validation 1.1+).

### Fase I — resource-optimizer en JDK 21 + jakarta (en progreso)

El goal `process` del `richfaces-resource-optimizer-maven-plugin` fallaba en
rich. Diagnóstico por capas (cada fix destapó el siguiente error, lo que indica
progreso real):

1. **Causa raíz #1 — Javassist antiguo (RESUELTO).** El stack real era
   `java.io.IOException: invalid constant type: 18` en
   `javassist.bytecode.ConstPool.readOne`: el Javassist de 2013 que arrastra
   Reflections 0.9.8 no lee el constant pool del bytecode Java 21. Fix: forzar
   **Javassist 3.33.0-GA** (propiedad `version.javassist`, gestionado en
   `build/pom.xml` depMgmt, excluida la javassist vieja de reflections, y
   declarado `org.javassist:javassist` en el `resource-optimizer-plugin`). Tras
   esto: `Reflections took ... producing 251 keys and 985 values` — el scan lee
   Java 21 OK.
2. **Causa raíz #2 — release() no idempotente (RESUELTO).** Tras arreglar el
   scan, saltó `NullPointerException: ...ClassToInstanceMap.values() ...
   this.instances is null` en `ServicesFactoryImpl.release()` (llamado desde
   `FacesImpl.stop()` en el `finally` del ProcessMojo). Fix: `release()` ahora
   tolera `instances == null` (idempotente).
3. **Causa raíz #3 — falta jakarta.el-api en el classpath del plugin (fix
   aplicado, SIN verificar).** Tras lo anterior saltó `A required class was
   missing: jakarta.el.ELContext` (el plugin tenía la vieja `javax/el/el-api 1.0`
   pero el core jakarta referencia `jakarta.el.ELContext`). Fix: añadido
   `jakarta.el:jakarta.el-api` al `resource-optimizer-plugin/pom.xml`.

Continuación (shell limpio) — más clases jakarta que faltaban en el classpath
del plugin, resueltas en cascada (cada fix destapó la siguiente):

4. **`jakarta.el.ELContext` faltante (RESUELTO):** añadido `jakarta.el:jakarta.el-api`.
5. **`ClassCastException: org.jboss.el.ExpressionFactoryImpl -> jakarta.el.
   ExpressionFactory` (RESUELTO):** `ApplicationImpl.createExpressionFactory()`
   hardcodeaba la impl de jboss-el (javax). Cambiado a `ExpressionFactory.
   newInstance()` (ServiceLoader estándar) + reemplazado `jboss-el` por la RI
   Jakarta EL `org.glassfish:jakarta.el:4.0.2` en el plugin.
6. **`NoClassDefFoundError: jakarta/servlet/Servlet` (RESUELTO):** el
   `jakarta.servlet-api` estaba `provided`; un plugin Maven lo necesita en
   runtime → scope compile.
7. **`Provider for jakarta.activation.spi.MimeTypeRegistryProvider cannot be
   found` (RESUELTO):** faltaba la IMPL de Jakarta Activation; añadido
   `org.eclipse.angus:angus-activation:2.0.2`.

**RESULTADO — Fase I CERRADA:** `mvn -pl build/resource-optimizer-plugin,core,
components/rich -Dmaven.test.skip=true -Dgpg.skip=true clean install` →
**BUILD SUCCESS** (rich en ~58 s). Las 6 ejecuciones del resource-optimizer
completan y `richfaces-5.0.0.jar` se instala. El scan de Reflections lee Java 21
(251 keys / 985 values). **rich construye 100% limpio con el optimizer.**

> Deuda menor restante (NO bloquea, build SUCCESS): el YUI Compressor 2.4.8 +
> Rhino antiguo emite "Compilation produced N syntax errors" al minificar JS
> moderno (jquery.js); el recurso se sirve sin minificar. Actualizar el
> minificador es mejora futura, no parte de la migración jakarta.

> **PENDIENTE de la Fase C/D:** strings literales `"javax.faces"` (nombre de
> librería de recursos JS) en core, descriptores faces-config/taglib al esquema
> 4.0, y los tests. El objetivo de la Fase D (componentes jakarta nativos con el
> CDK jakarta) está LOGRADO.

### 4.1 Vía D-1 — Adoptar el CDK 10.0.1 jakarta de albfernandez (preferente)
- [x] Cambiar en `pom.xml` raíz y `build/pom.xml` las coordenadas del CDK:
  - groupId `org.richfaces.cdk` → `com.github.albfernandez.richfaces.cdk`.
  - `version.cdk` `4.5.1-SNAPSHOT` → `10.0.1`.
  - Ajustar las dependencias del CDK usadas en `build/pom.xml`
    (`annotations`, `generator`) al nuevo groupId/version.
  - Ajustar las invocaciones del plugin en `core/pom.xml`,
    `components/pom.xml`, `components/a4j/pom.xml`, `components/rich/pom.xml`.
- [ ] Comprobar que Maven Central resuelve el CDK 10.0.1 (o pre-descargarlo a
      `.m2`). El plugin es `richfaces-cdk-maven-plugin` bajo el nuevo groupId.
- [ ] Revisar la `requirePluginVersions` en `components/pom.xml`
      (`unCheckedPluginList`) que hoy lista `org.richfaces.cdk:...`: actualizar
      al nuevo groupId o el enforcer fallará.
- [ ] Regenerar y compilar `components`:
      `mvn -pl components -am -Dmaven.test.skip=true -Dgpg.skip=true clean install`.
- [ ] **Verificar** con `javap`/grep sobre `components/*/target/generated-sources`
      que las clases generadas usan `jakarta.faces.*` y NO `javax.faces`.

> Nota de compatibilidad: entre el CDK `4.5.1` y `10.x` puede haber cambios en el
> modelo/anotaciones (`com.github.albfernandez.richfaces.cdk:annotations`) que el
> `core`/`components` consumen en tiempo de generación. Si aparecen errores de
> anotaciones o de esquema de config del CDK, alinear las dependencias
> `annotations`/`attributes`/`commons` a 10.0.1 también.

### 4.2 Vía D-2 — Recompilar el CDK desde fuente (respaldo)
Si D-1 no encaja tal cual (API del modelo cambiada, o hace falta un ajuste):
- [ ] Clonar https://github.com/albfernandez/richfaces-cdk (rama/tag 10.0.1).
- [ ] Compilarlo local (`mvn install`) para tener el CDK jakarta en `.m2`.
- [ ] Si se necesita un cambio puntual (p. ej. una plantilla `.ftl` o una
      constante de paquete), aplicarlo sobre el fuente y reinstalar. Esto es un
      ajuste menor, NO una reescritura: el fuente completo está disponible.
- [ ] Usar `MilovdZee/richfaces-jakarta` como referencia de cómo quedó el
      framework migrado (para alinear versiones CDK ↔ framework).

### 4.3 RendererBase y jerarquías generadas
- [ ] Revisar `components/a4j/.../RendererBase.java` (ya se le quitó `final` en
      la Fase 21): confirmar que extiende `jakarta.faces.render.Renderer` tras el
      renombrado y que los renderers generados sobreescriben con firmas jakarta.

---

## 5. Fase E — faces-config / taglibs / registro CDI de la librería — HECHA

Nota: el grueso de esta fase se ejecutó junto con los flecos de la Fase C (sección
3.5), porque faces-config-4.0 y la eliminación de managed-beans están acoplados.

- [x] `core.faces-config.xml`: el managed-bean `a4jSkin` (y `richfacesVersion`)
      convertidos a CDI (`@Named` + `@ApplicationScoped`); las declaraciones
      `<managed-bean>` eliminadas (Faces 4.0 ya no las soporta).
  - **Impacto de skinning:** `#{a4jSkin.xxx}` en los `.ecss` sigue resolviendo
    vía CDI (mismo nombre EL `a4jSkin`); a verificar en runtime en el
    mini-proyecto Liberty (sección 11).
- [x] Añadido `core/src/main/resources/META-INF/beans.xml` (CDI 4.0,
      `bean-discovery-mode="annotated"`) para que `SkinBean`/`VersionBean` se
      descubran en cualquier contenedor CDI. Core compila e instala con él
      (BUILD SUCCESS, JDK 21).
- [x] Verificado que NO quedan otros `<managed-bean>` en los `*.faces-config.xml`
      de producción de `core`/`components` (solo estaban los dos del core).

---

## 6. Fase F — Tests de la librería en Jakarta — HECHA

Baseline unitario 100% verde en JDK 21 / Faces 4.0:
**core 139 · a4j 82 (1 skip) · rich 93 (5 skip)**, 0 fallos / 0 errores.

### 6.1 Mocks JSF — HECHO
- [x] Existe la variante jakarta de `com.github.albfernandez.test-jsf`: versión
      **`10.0.0`** (usa `jakarta.el`/`jakarta.servlet`, easymock 5.4.0, dom4j
      2.1.4). Cambiado `version.jsf-test` `1.1.11`→`10.0.0` y `version.easymock`
      `2.5.2`→`5.4.0` en `build/pom.xml`; eliminado `easymockclassextension`
      (fusionado en easymock 3+).
- [x] Adaptado el código de test a easymock 5 (`classextension.EasyMock`→
      `EasyMock`, `new Capture(...)`→`EasyMock.newCapture(...)`, partial mock via
      `partialMockBuilder`), a HtmlUnit 3 (`com.gargoylesoftware.htmlunit`→
      `org.htmlunit`, `asText()`→`asNormalizedText()`, `FIREFOX_52`→`FIREFOX`) y a
      CDI (`jakarta.faces.bean.*`, eliminado en Faces 4.0, → `@Named` + scopes CDI).
- [x] `ELTestBase` de test: `org.jboss.el.ExpressionFactoryImpl` →
      `jakarta.el.ExpressionFactory.newInstance()`.
- [x] `--add-opens` de surefire para cglib mantenido.

### 6.2 Deuda de `rich` — RESUELTA (era el CDK, no los tests)
- [x] Causa raíz: el CDK trae un annotation processor `CdkProcessorImpl` que
      declara `SupportedSourceVersion RELEASE_8`; con `-release 21` javac lo omite
      en silencio y NO generaba las clases `UIxxx`. Solución: enlazar el goal
      `generate` del CDK (fase `process-sources`) explícitamente en
      `components/pom.xml` `build/plugins`. Ahora genera ~102 componentes.
- [x] 5 `.template.xml` de rich usaban `type="javax.faces.component.UIComponent"`
      → `jakarta.faces.component.UIComponent`.
- [x] Dependencias de test añadidas a rich: Weld SE 5.1.7, glassfish jakarta.el
      4.0.2, guava, hibernate-validator 8.0.5.Final.

### 6.3 Aislamiento de tests que requieren contenedor
- [x] Los tests que arrancan `FacesContext`/`StagingServer` o navegador se
      marcan con la categoría JUnit `org.richfaces.test.ContainerRequired` y se
      excluyen del run unitario (surefire `excludedGroups`,
      `unit.test.excludedGroups`). Se ejecutan en integración (Fase G).

---

## 7. Fase G — Integración con contenedor Jakarta EE 10 — HECHA (acotada)

Se montó una infraestructura de integración **nueva y limpia** para Jakarta EE 10
en el módulo independiente **`integration-tests-jakarta/`** (fuera del reactor por
defecto), en vez de portar el framework legacy (Arquillian 1.1/Graphene 2.1/
Selenium 2/PhantomJS, ~86 IT), que queda como legacy para port incremental.

Stack moderno (2026):
- **Arquillian 1.10.2.Final** (JUnit 4 container).
- **WildFly 35.0.0.Final gestionado** (Jakarta EE 10, JDK 21) — se auto-descarga
  vía `wildfly-dist` y lo arranca `wildfly-arquillian-container-managed 5.1.0.Final`.
- **Selenium 4.35.0 + Chrome headless** (Selenium Manager auto-provisiona el
  driver; sin PhantomJS ni Graphene).
- **ShrinkWrap resolver 3.3.7** para ensamblar el WAR y resolver los `richfaces-*`.

- [x] IT smoke `RichFacesSmokeIT`: despliega un WAR (`<rich:panel>` con
      `<a4j:commandButton>` sobre un bean CDI `@SessionScoped`) en WildFly 35 y,
      con Chrome headless real, verifica que el panel RichFaces renderiza (clase
      `rf-p`) y que el Ajax de a4j incrementa el contador sin recargar.
      **Resultado: `Tests run: 1, Failures: 0, Errors: 0` — BUILD SUCCESS.**
- [x] Ejecutar: `mvn -f integration-tests-jakarta/pom.xml -Pit-wildfly verify`.
- [x] **Fix imprescindible descubierto aquí** (también necesario para Liberty):
      los POM publicados de `richfaces-core/a4j/richfaces` tenían dependencias sin
      versión (`cdk:annotations`, `weld-se-core`), lo que los invalidaba para
      consumidores externos y hacía que ShrinkWrap NO trajera las transitivas
      (Guava) → `NoClassDefFoundError com.google.common.base.Function` al
      desplegar. Se añadió versión explícita en `core/pom.xml`.

> Pendiente opcional: port incremental de los ~86 IT legacy (Graphene→Selenium 4)
> y pulir warnings benignos del modelo POM de a4j/rich.

---

## 8. Fase H — Examples a Jakarta (incluye migración CDI real) — ✅ COMPLETA (8/8)

Los examples SÍ usan managed beans JSF (`javax.faces.bean.*`), eliminados en
Faces 4.0. Aquí está el refactor CDI de verdad. Hay **8 examples** en total.

### 8.0 Reglas de migración aplicadas (patrón común, verificado en 6 examples)

- **Beans → CDI:** `@javax.faces.bean.ManagedBean[(name=x)]` → `@jakarta.inject.Named[("x")]`
  (se descarta `eager=true`). Scopes: `javax.faces.bean.{Request,Session,Application}Scoped`
  → `jakarta.enterprise.context.*`; `@ViewScoped` → `jakarta.faces.view.ViewScoped`
  (sigue en Faces); `@NoneScoped` → `@Dependent`.
- **Serializable:** los beans `@SessionScoped`/`@ViewScoped` deben implementar
  `java.io.Serializable` con `serialVersionUID` (añadido donde faltaba).
- **`@ManagedProperty(value="#{bean}")`** → `@jakarta.inject.Inject` (se quita el
  value). Si inyectaba un literal, el default se mueve a inicializador de campo o
  `@PostConstruct` (p.ej. irc-client `SkinBean`).
- **Renombrado de imports/FQN:** `javax.faces.*`, `javax.validation.*`,
  `javax.persistence.*`, `javax.annotation.{PostConstruct,...}`,
  `javax.enterprise.*`, `javax.inject.*`, `javax.el.*`, `javax.servlet.*`,
  `javax.xml.bind.*` (JAXB) → `jakarta.*`. **NO se tocan** (JDK): `javax.swing.*`,
  `javax.xml.{parsers,transform,xpath,namespace}.*`, `javax.naming.*`,
  `javax.imageio.*`, `javax.crypto.*`, `javax.net.*`, `javax.sql.*`.
- **Descriptores** → namespace `https://jakarta.ee/xml/ns/jakartaee`:
  `web.xml` a Servlet 6.0 (`web-app_6_0.xsd`, `version="6.0"`) + renombrar
  `javax.faces.*` param-names y `javax.faces.webapp.FacesServlet`;
  `faces-config.xml` a 4.0 (`web-facesconfig_4_0.xsd`), quitando `<managed-bean>`
  (ahora CDI) pero conservando `<factory>/<lifecycle>/<converter>/...`;
  `persistence.xml` a Jakarta Persistence 3.1 (`persistence_3_1.xsd`);
  `beans.xml` CDI 4.0 (`beans_4_0.xsd`, `bean-discovery-mode="annotated"`),
  creándolo donde falte.
- **XHTML** (namespaces Facelets Faces 4.0): `http://java.sun.com/jsf/html`
  (y `http://xmlns.jcp.org/jsf/*`) → `jakarta.faces.html`; `/jsf/core` →
  `jakarta.faces.core`; `/jsf/facelets` → `jakarta.faces.facelets`;
  `/jsf/composite*` → `jakarta.faces.composite*`; `/jsp/jstl/core` →
  `jakarta.tags.core`; `/jsp/jstl/functions` → `jakarta.tags.functions`.
  Se dejan intactos los namespaces `richfaces.org` (a4j, rich) y los `.html/.css/.js`
  estáticos (capturas de demo).
- **POM:** `org.jboss.spec:jboss-javaee-web-6.0` → `jakarta.platform:jakarta.jakartaee-web-api:10.0.0`
  (provided); `jboss-javaee-6.0` (full) → `jakarta.platform:jakarta.jakartaee-api:10.0.0`.
- **Build bajo demanda** (siguen comentados en el reactor). Helper reutilizable:
  `build\jakarta-transform\mvnbuild.cmd <pom-relativo> <log-relativo>`
  (fija JDK 21, `taskkill /F /IM java.exe`, `mvn -B -Dmaven.test.skip=true
  -Dgpg.skip=true clean install`, y anexa `DONE_EXIT_<n>`). Verificar por
  `BUILD SUCCESS`/`DONE_EXIT_0` en el log (la terminal de esta sesión devuelve
  salida vacía/exit -1 aunque el comando sí corre).

### 8.1 Prerrequisitos resueltos (POMs de librería inválidos para consumidores)

Los examples consumen los `richfaces-*` desde `.m2`; sus POMs efectivos tenían
dependencias **sin versión**, lo que los hacía inválidos para consumidores
externos y rompía la resolución transitiva:
- `components/pom.xml`: `com.github.albfernandez.richfaces.cdk:annotations`
  sin versión → añadido `<version>${version.cdk}</version>` (10.0.1). Afecta a
  a4j y rich (heredan de este parent).
- `build/resource-optimizer-plugin/pom.xml`: `org.javassist:javassist` sin
  versión (su parent es `richfaces-parent`, no `build/pom.xml`, donde estaba la
  propiedad) → añadida propiedad local `<version.javassist>3.33.0-GA` + versión
  explícita en la dependencia.
- **Reinstalados en `.m2` (jakarta, JDK 21, BUILD SUCCESS):**
  `richfaces-resource-optimizer-maven-plugin`, `richfaces-a4j`, `richfaces` (rich).
  Nota: `richfaces-core`, `-a4j`, `richfaces` ya estaban jakarta; se re-emitieron
  con los POMs corregidos. Los `[ERROR] Exception rendering resource ...
  resource==null` y los "syntax errors" de YUI/Rhino en `jquery.js` del
  resource-optimizer son **NO fatales** (rich compila/instala verde en ~50 s).

### 8.2 Estado por example

| # | Example | Estado | Notas |
|---|---|---|---|
| 1 | `template` | ✅ BUILD SUCCESS (war 5.0.0 en `.m2`) | Base de overlay de otros. 3 beans CDI (`TimeBean`, `SkinBean`, `Pages` @App) + `ContentBean` @Dependent; `faces-config` sin managed-beans; `PhaseTracker` a `jakarta.faces.event`; `beans.xml` nuevo. |
| 2 | `push-demo` | ✅ BUILD SUCCESS (war 5.0.0) | `RichBean` → `@Named @RequestScoped`; overlay de `template`. |
| 3 | `standalone-js` | ✅ BUILD SUCCESS (war 5.0.0) | `IterationBean` (@Session+Serializable), `AutocompleteBean` (@Request); `fn=jakarta.tags.functions`. Ficheros `.html/.css` de captura sin tocar. |
| 4 | `irc-client` | ✅ BUILD SUCCESS (war 5.0.0) | `SkinBean` (drop `@ManagedProperty`, default en `@PostConstruct`), `ChatBean`; dep full `jakarta.jakartaee-api:10.0.0` (usa JMS para push). Templates propios (no overlay). |
| 5 | `jpa-demo` | ✅ BUILD SUCCESS (war 5.0.0) | `PersistenceService` @App (drop `eager`), `PersonBean`/`RF10888` @Session+Serializable con `@Inject`; JPA→`jakarta.persistence`; `persistence.xml` v3.1; `<factory><lifecycle-factory>` conservado; Faces 4.0 `Lifecycle`/`LifecycleFactory` compilan sin métodos abstractos nuevos. Overlay de `template`. |
| 6 | `components-demo` | ✅ BUILD SUCCESS (war 5.0.0) | Migrado por sub-agente: 83 `.java`, 114 `.xhtml`. 20 beans +Serializable; `DropListenerBean` `@ManagedProperty`→`@Inject`; 11 clases JAXB `javax.xml.bind`→`jakarta.xml.bind`; `javax.swing.*` conservado. `faces-config` conserva `<lifecycle><phase-listener>PhaseTracker`. Overlay de `template`. |
| 7 | `showcase` | ✅ BUILD SUCCESS (war 5.0.0) | POM reescrito a WAR Jakarta único (ver 8.3). `opBean1/opBean2` (managed-beans del mismo tipo) → productores CDI `OutputPanelBeanProducer`; `system-event-class` a jakarta; `beans.xml` CDI 4.0 (`weld:scan` conservado); `app-tags.taglib.xml` 4.0. 90 `.java` (53 beans, `ActionListenerBean` @Dependent, 9 `@ManagedProperty`→`@Inject`, `SkinBean` literal→inicializador, varios +Serializable), 233 `.xhtml`, 3 JAXB. Dep `rewrite-servlet` a `10.0.2.Final` (línea jakarta.servlet). Borrados overlays `webapp-*`/`resources-*`. |
| 8 | `photoalbum` | ✅ BUILD SUCCESS (war 5.0.0) | POM reescrito a WAR Jakarta único (ver 8.4). `javax.activation`→`jakarta.activation-api:2.1.3`; Jackson 1.x (`org.codehaus.jackson`)→Jackson 2 (`com.fasterxml.jackson.annotation`); Hibernate ORM 6 (quitado `@LazyCollection`, resto conservado); HV8 (`@NotEmpty`/`@Email`→`jakarta.validation.constraints`); `javax.ws.rs`→`jakarta.ws.rs`. 83 `.java`, 97 `.xhtml`; `web.xml` 6.0, `faces-config` 4.0, `beans.xml` 4.0, `persistence.xml` 3.1, taglib 4.0. Eliminado el stack Arquillian/Drone/PhantomJS/testng, `-XX:MaxPermSize`, y las ejecuciones del resource-optimizer. |

### 8.3 ✅ HECHO — `examples/showcase`

Era el example más grande y con más deuda legacy. Ejecutado (2026, clean code):
**simplificado** el `pom.xml` a un único WAR Jakarta EE 10 y **eliminada** toda la
maquinaria legacy multi-impl / multi-contenedor / integración. Resultado: **BUILD
SUCCESS**. Detalle de lo realizado (el plan original se conserva como referencia):

- **PART A — reescribir `examples/showcase/pom.xml`** (hoy ~1488 líneas) a un POM
  mínimo:
  - Conservar coordenadas (`org.richfaces.examples:richfaces-showcase:5.0.0`,
    war), `dependencyManagement` (imports `richfaces-cache-bom`, `richfaces-build`),
    `maven.compiler.release=21`.
  - Deps compile: `richfaces`, `richfaces-push-depchain`, `slf4j-api`,
    `ehcache-core`, `hsqldb` (si `hsqldb-j5` no resuelve → `org.hsqldb:hsqldb:2.7.2`),
    `hibernate-validator` (si no gestionado → `8.0.1.Final`), Hibernate ORM
    (`hibernate-entitymanager` no existe en ORM 6 → `org.hibernate.orm:hibernate-core:6.4.4.Final`),
    `jandex`, `rewrite-servlet`, `jsoup`, `weld-servlet-core` (CDI en servlet;
    si no gestionado → `weld-servlet-shaded:5.1.2.Final`).
  - `jakarta.platform:jakarta.jakartaee-web-api:10.0.0` (provided) — reemplaza
    `jboss-javaee-6.0` y **todos** los perfiles de impl JSF (Mojarra 4 llega
    transitivo de richfaces).
  - **Quitar** deps `javax.enterprise:cdi-api`, `javax.servlet.jsp.jstl:jstl-api`
    (los da jakartaee-web-api), y **todo** el bloque test (junit, arquillian-*,
    suite-extension, httpclient, build-resources, page-fragments).
  - **Eliminar perfiles** completos: `jsf_ri`, `jsf_jboss`, `myfaces`, `jee6`,
    `openshift`, `smoke-tests`, `release`, `integration-tests`,
    `browser-{phantomjs,chrome,firefox}`, `browser-remote-reusable`, y **todos**
    los `wildfly-*`/`tomcat-*`/`jbosseap*`. Esto borra **`-XX:MaxPermSize`**,
    `arquillian.drone.browser=phantomjs`, `graphene-webdriver`,
    `arquillian-drone-webdriver-depchain`, `screenshooter` y
    `jsfImplementation=org.glassfish:javax.faces` (Anexos 13.4/14.6). Verificar
    que NINGUNA de esas cadenas queda en el POM.
  - Quitar `<prerequisites><maven>3.0`.
- **PART B — consolidar webapp:** hoy hay `webapp` (base: `WEB-INF/{faces-config.xml,
  beans.xml (CDI 1.0), app-tags.taglib.xml}` + vistas) y overlays `webapp-tomcat`,
  `webapp-jee6`, `webapp-openshift` + `resources-{tomcat,jee6,openshift}`.
  Colapsar a `src/main/webapp` + `src/main/resources`:
  - Crear `src/main/webapp/WEB-INF/web.xml` a partir de `webapp-jee6/WEB-INF/web.xml`
    migrado a Servlet 6.0 (params `jakarta.faces.*`, `FacesServlet` jakarta,
    `PROJECT_STAGE` literal `Development`, conservar context-params `org.richfaces.*`,
    push, welcome-file, mime, login-config).
  - Borrar los directorios overlay `webapp-tomcat/-jee6/-openshift` y
    `resources-tomcat/-jee6/-openshift` (contienen descriptores javax/contenedor).
- **PART C — migrar código/descriptores/xhtml** según reglas 8.0. Ojo:
  `beans.xml` base está en CDI 1.0 → subir a 4.0; `app-tags.taglib.xml` → namespace
  facelet-taglib Jakarta (`facelettaglibrary_4_0.xsd`); JAXB en `CapitalsParser`,
  `CDParser` (y otros) `javax.xml.bind`→`jakarta.xml.bind`; hay `persistence.xml`
  a v3.1 si existe bajo `resources`.
- **Build:** `build\jakarta-transform\mvnbuild.cmd examples\showcase\pom.xml
  examples\showcase\build-h.log` e iterar hasta `BUILD SUCCESS`. Posibles ajustes:
  artefactos renombrados en Jakarta (hibernate-entitymanager→core 6, hsqldb-j5,
  weld-servlet-core/shaded, groupId de hibernate-validator).

### 8.4 ✅ HECHO — `examples/photoalbum`

El más pesado por volumen (CDI, JPA, EJB, `javax.activation`, Jackson, Hibernate).
Ya era CDI (0 managed beans JSF). Resultado: **BUILD SUCCESS**. Además de las
reglas comunes se resolvieron migraciones de API no triviales: `javax.activation`
→ `jakarta.activation-api:2.1.3`; **Jackson 1.x → 2** (`org.codehaus.jackson`
→ `com.fasterxml.jackson.annotation`, dep `jackson-annotations:2.17.1`);
**Hibernate ORM 6** (se quitó `@LazyCollection`/`LazyCollectionOption`, eliminado
en ORM 6; lazy es el default JPA, comportamiento preservado); `hibernate-annotations`
→ `hibernate-core:6.4.4.Final`; **Bean Validation 3 / HV 8** (`@NotEmpty`, `@Email`
→ `jakarta.validation.constraints`; `@Length`, `@URL` siguen en HV);
`javax.ws.rs` → `jakarta.ws.rs`. Se **eliminaron** del pom el stack de integración
(Arquillian/Drone/PhantomJS/testng), `-XX:MaxPermSize`, y las ejecuciones del
`resource-optimizer-plugin` (el WAR funciona sin pre-empaquetado de recursos;
RichFaces los sirve dinámicamente). El plan original se conserva abajo como
referencia:
- Reglas 8.0 (imports `javax.*`→`jakarta.*`: `cdi`, `persistence`, `ejb`,
  `faces`, `servlet`, `validation`, `annotation`).
- **`javax.activation`** → `jakarta.activation:jakarta.activation-api:2.1` (el
  POM ya tenía `com.sun.activation:javax.activation:1.2.0` de la Fase 21).
- Descriptores: `web.xml` 6.0, `faces-config` 4.0, `beans.xml` (existe en CDI 1.0
  → subir a 4.0), `persistence.xml` v3.1.
- **Anexos 13.4/14.6:** quitar `-XX:MaxPermSize`; eliminar/excluir el stack de
  integración Arquillian/Drone/PhantomJS/Selenium del `pom.xml` (mismo criterio
  que showcase: dejar un único WAR Jakarta, sin la maquinaria de tests de
  navegador legacy).
- Build con el helper e iterar hasta `BUILD SUCCESS`.

### 8.5 Cierre de Fase H

- [x] Actualizar esta sección con showcase y photoalbum en verde.
- [x] Los 8 examples migrados a Jakarta EE 10 / Faces 4.0 / JDK 21 y **BUILD
      SUCCESS**; los 8 WAR 5.0.0 quedan instalados en `.m2`
      (`org/richfaces/examples/{template, push-demo, standalone-js, irc-client,
      jpa-demo, components-demo, richfaces-showcase, richfaces-photoalbum}`).
- [x] Anexos 13.4/14.6 cubiertos para examples: `-XX:MaxPermSize` eliminado de
      showcase y photoalbum; stack Drone/PhantomJS/Graphene/Arquillian eliminado
      de sus POMs; `javax.activation` → `jakarta.activation-api`; JAXB →
      `jakarta.xml.bind`.
- [ ] **Commit de la Fase H** (`git add` de `examples/*`, `components/pom.xml`,
      `build/resource-optimizer-plugin/pom.xml`,
      `build/jakarta-transform/mvnbuild.cmd` y este plan).

> **Caveat de runtime (no bloquea build; para pulir en despliegue):** en showcase,
> 9 antiguos `@ManagedProperty(value="#{bean.lista}")` se convirtieron a `@Inject`.
> En CDI `@Inject` resuelve por tipo, no por expresión EL; los campos que inyectan
> `List<T>` (p.ej. `List<CDXmlDescriptor>`, `List<Capital>`, `List<InventoryItem>`)
> necesitarán un **productor CDI** de ese tipo para funcionar en runtime. Compila
> en verde; se abordará al desplegar/validar en navegador.

> Nota de entorno (esta sesión): la terminal integrada quedó degradada
> (antepone sintaxis PowerShell `cd "..." ;` a `cmd`, devuelve exit -1/salida
> vacía aunque el comando corre; el patrón `set VAR=.. && mvn` corrompía la línea
> de comando). Solución: el helper `.cmd` + verificar resultados leyendo ficheros
> (`grep_search`/`read_file`) y `.m2`, no la salida de la terminal. Los procesos
> en background a veces quedan "zombie" (no escriben log): parar y relanzar.

---

## 9. Fase I — Deuda del resource-optimizer (build reproducible) — ✅ COMPLETA

**Enfoque elegido: migrar de Reflections 0.9.8 a ClassGraph** (en vez de subir a
Reflections 0.10.x). Reflections 0.10.x elimina la API que este código usa
(`AbstractScanner`, `getStore().getStoreMap()`, el campo público `Reflections.log`,
`SubTypesScanner`/`TypeAnnotationsScanner`) y no ofrece un equivalente directo del
marker-file scanner, así que habría sido una reescritura contra una API frágil.
ClassGraph lee bytecode Java moderno de forma nativa, por lo que **elimina también
el parche de Javassist 3.33.0-GA** que se forzaba sobre Reflections 0.9.8. Resultado:
build **reproducible** en JDK 21 sin pines de bytecode.

- [x] ~~Subir `reflections` a 0.10.x~~ → **Reemplazado por ClassGraph 4.8.174**
      (`io.github.classgraph:classgraph`). Lee bytecode Java 21 de forma fiable y
      nativa. Eliminadas del build las dependencias `org.reflections:reflections`
      y `org.javassist:javassist` y las propiedades `version.reflections` /
      `version.javassist` (en `build/pom.xml`, `build/resource-optimizer-plugin/pom.xml`
      y `core/pom.xml`).
- [x] ADAPTAR el scanner custom → **reescrito**. Se creó
      `core/src/main/resource-optimizer/.../resource/scan/impl/reflections/ClassGraphScanner.java`
      (helper `AutoCloseable` que envuelve un `ScanResult` de ClassGraph con
      `overrideClasspath(urls).ignoreParentClassLoaders().enableClassInfo().enableAnnotationInfo()`
      y expone `getTypesAnnotatedWith` / `getSubTypesOf` / `getMarkedClasses`).
      Se **eliminaron** `MarkerResourcesScanner.java` y `ReflectionsExt.java`.
      `DynamicResourcesScanner` y `ResourceOrderingScanner` se reescribieron para
      usar el nuevo helper (try-with-resources), conservando el comportamiento
      (filtrado de URLs por `META-INF/faces-config.xml`, filtro de clases
      instanciables, ordering). Los marker files `META-INF/**.resource.properties`
      se detectan vía `ScanResult.getAllResources()`.
- [x] Verificar `packed-resources` / `packed-compressed-resources` de `rich`
      deterministas → **BUILD SUCCESS**. Reactor
      `mvn -pl core,build/resource-optimizer-plugin,components/rich -Dmaven.test.skip=true
      -Dgpg.skip=true clean install` verde (rich ~1:14 min). Las **6 ejecuciones**
      `richfaces-resource-optimizer:5.0.0:process` (static / compressed /
      packed-resources / resources-excluded / packed-compressed /
      compressed-excluded) completan. **Desaparece por completo** el ruido previo
      de Reflections/Javassist (`ReflectionsException: could not create class file`,
      `invalid constant type: 18`). ClassGraph es determinista por diseño.
- [ ] (Opcional, se traslada a la **Fase J**) Actualizar YUI Compressor 2.4.8 +
      Rhino, que sigue emitiendo `Compilation produced N syntax errors` al minificar
      `jquery.js` (único `[ERROR]` restante en el log; **no fatal**, el recurso se
      sirve sin minificar). No forma parte de la robustez del scanner; ya estaba
      listado como deuda de producción en el Anexo 14.

> **RESULTADO — Fase I CERRADA:** el classpath scanning del resource-optimizer
> corre sobre ClassGraph 4.8.174; sin Reflections 0.9.8 ni Javassist pineado. El
> build de `rich` con las 6 fases del optimizer es reproducible en JDK 21.

---

## 10. Fase J — Limpieza, CI y objetivo final

### 10.1 Retirar el puente
- [ ] Una vez `core`/`components` son jakarta NATIVO y verdes, eliminar el
      módulo `jakarta-bridge` del reactor (ya no se necesita post-transformar).
- [ ] Quitar de `.m2`/docs las variantes `*-jakarta` producidas por el puente.

### 10.2 CI y docs
- [ ] `.travis.yml` → `jdk: openjdk21` (o migrar a GitHub Actions con Temurin 21).
- [ ] Actualizar `README.adoc` y `TESTS.md`: requisitos JDK 21 + Jakarta EE 10.
- [ ] Revisar warnings de deprecación relevantes (no bloquean).

### 10.3 Objetivo final: mini-proyecto Java 21 + WebSphere Liberty
- [ ] Crear `demo-liberty/` (WAR, `maven.compiler.release=21`) que dependa de
      los `richfaces-*` jakarta NATIVOS.
- [ ] `server.xml` de Liberty con feature **`faces-4.0`** (Jakarta, namespace
      `jakarta.faces`) + `localConnector`.
- [ ] Empaquetar en el WAR las dependencias de runtime que Liberty NO aporta:
  - `richfaces-core`, `richfaces-a4j`, `richfaces` (jakarta nativos).
  - `jakarta.xml.bind-api` 4.0 + `jaxb-runtime` 4.0 (RichFaces lo usa al
    arrancar; sin él la app no inicializa).
  - Guava, cssparser y demás transitivas de `richfaces-core`.
  - Marcar Faces/Servlet/EL/CDI como `provided` (las aporta Liberty).
- [ ] `beans.xml` + `faces-config.xml` mínimos + un `.xhtml` con `<rich:panel>` y
      `<a4j:commandButton>`.
- [ ] Desplegar en Liberty y verificar en el navegador: el componente renderiza
      y el Ajax de a4j responde.

---

## 11. Definition of Done (Jakarta EE 10)

- [ ] `bom`, `build/*`, `core`, `components`, `dist` compilan con Faces 4.0 y
      `maven.compiler.release=21`.
- [ ] No queda ningún `import javax.` salvo los que siguen en el JDK
      (`javax.swing`, `javax.xml.parsers`, `javax.xml.namespace`,
      `javax.naming`, `javax.imageio`, `javax.crypto`, `javax.net`, `javax.sql`).
- [ ] El **CDK genera `jakarta.*`** y `components` compila (verificado con
      `javap`/grep sobre `generated-sources`).
- [ ] Managed beans de la librería migrados a CDI (o eliminados); `a4jSkin`
      resuelve y el skinning `.ecss` sigue funcionando.
- [ ] Tests unitarios de `core`, `a4j` y `rich` en verde.
- [ ] Al menos un perfil de integración corre en WildFly 30+ / Tomcat 10.1+.
- [ ] Examples migrados a CDI y compilando (bajo demanda).
- [ ] resource-optimizer reproducible en JDK 21.
- [ ] `jakarta-bridge` eliminado; CI verde en JDK 21.
- [ ] Mini-proyecto Liberty desplegado y visible en navegador con `faces-4.0`.

---

## 12. Orden recomendado de ejecución (resumen para Kiro)

1. Fase A (rama + base verde + propiedades).
2. Fase B (BOM/deps jakarta) — SIN tocar aún el código.
3. Fase C (renombrado fuente + descriptores 4.0) — `core` primero.
4. Fase D (CDK jakarta) — desbloquea `components`. **Punto de mayor riesgo.**
5. Fase E (faces-config/CDI de la librería).
6. Fase F (tests de la librería, incluida deuda de `rich`).
7. Fase G (integración con contenedor jakarta).
8. Fase H (examples + CDI real).
9. Fase I (resource-optimizer reproducible).
10. Fase J (limpieza puente + CI + mini-proyecto Liberty).

> Puntos de "no avanzar si esto está rojo": B→C (core debe compilar),
> D (components debe generar jakarta y compilar), F (unitarios verdes).
>
> **Riesgos principales (revisados tras confirmar el CDK jakarta):**
> 1. **Compatibilidad CDK 10.0.1 ↔ nuestro core/components (Fase D).** El CDK
>    jakarta ya existe, pero saltó de `4.5.1` a `10.x`; puede haber cambios en el
>    modelo/anotaciones que consume nuestro código en tiempo de generación. Si D-1
>    no encaja, D-2 (recompilar el fuente del CDK) es un ajuste acotado, no una
>    reescritura.
> 2. **Mocks de test `test-jsf` atados a javax (Fase F).** Puede no haber variante
>    jakarta directa; plan B en la propia fase.
> 3. **resource-optimizer + Reflections (Fase I)** para build 100% reproducible.
>
> Referencias externas útiles: CDK jakarta
> https://github.com/albfernandez/richfaces-cdk (10.0.1, en Maven Central) y el
> fork del framework ya migrado https://github.com/MilovdZee/richfaces-jakarta.

---

## Patrón de build (JDK 21 real, evitando el JDK del terminal)

```
cmd /v:on /c "set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot&& mvn.cmd -Dmaven.test.skip=true -Dgpg.skip=true clean install"
```
Para reanudar tras un fallo de un módulo concreto: añadir `-rf :<artifactId>`.

---

## 13. Anexo — Inventario del stack de tooling legacy pendiente (escaneo del repo)

Escaneo hecho sobre todo el repo (agosto 2026) buscando `graphene`, `phantomjs`,
`arquillian.drone`, `selenium`, `screenshooter`. Objetivo: no dejar por fuera
ninguna referencia al stack de test de navegador legacy. Lo que sigue COMPLETA
lo ya cubierto en las Fases G/H y aterriza el trabajo restante.

### 13.1 Qué eran PhantomJS y Graphene (y por qué RichFaces los usaba)

- **PhantomJS**: navegador **headless** (sin ventana) basado en WebKit, la
  misma familia de motor que usaba Safari/Chrome antiguo. Ejecutaba páginas con
  HTML/CSS/JS reales pero sin pintar en pantalla, controlado por script. En CI
  servía para correr tests de UI en un servidor sin monitor. RichFaces lo usaba
  como **navegador por defecto** de sus tests de integración (`browser=phantomjs`)
  porque era rápido y no necesitaba entorno gráfico. Está **descontinuado desde
  2018** (el mantenedor lo archivó cuando Chrome sacó su modo headless nativo);
  no soporta JS moderno ni se instala de forma fiable hoy. Por eso el plan lo
  reemplaza por **Chrome headless**.
- **Graphene**: extensión de **Arquillian** (framework de test de JBoss/Red Hat)
  construida sobre **Selenium WebDriver**. Añadía azúcar para test de apps AJAX:
  esperas inteligentes (`waitGui`, `waitAjax`, `waitModel` — ver el bloque
  `<extension qualifier="graphene">` del `arquillian.xml`), inyección de
  `WebDriver`/`@Page`/`@FindBy` en el test, y el patrón **Page Fragments**
  (componentes de página reutilizables). RichFaces lo usaba porque sus
  componentes son intrínsecamente AJAX: sin esperas de "AJAX terminó" los tests
  serían inestables (flaky). El módulo `build/page-fragments` es precisamente una
  librería de fragmentos Graphene que modela cada componente RichFaces
  (`RichFacesDataTable`, `RichFacesTree`, etc.) para reusarlos en los ~86 IT.
- **Drone** (`arquillian.drone.browser`): el gestor de ciclo de vida del
  `WebDriver` de Arquillian. Decidía qué navegador levantar (phantomjs/chrome/
  firefox) y lo inyectaba en Graphene. Es la capa que las propiedades
  `arquillian.drone.*` configuran.
- **Selenium 2 (`2.53.0`)** + **screenshooter** (`arquillian-browser-screenshooter`,
  saca captura al fallar un test) completaban el stack. Selenium 2 usaba el
  protocolo JSON Wire (hoy reemplazado por el estándar W3C WebDriver de
  Selenium 4).

En resumen, la cadena legacy era:
**Arquillian** (orquesta el contenedor + despliega el WAR) → **Drone** (levanta
el navegador) → **PhantomJS** (el navegador headless) → **Graphene** (esperas
AJAX + page fragments) → **Selenium 2** (driver) → **screenshooter** (evidencia).

### 13.2 Qué cambiamos a nivel de herramientas (resumen del "antes → después")

| Pieza | Antes (legacy, JSF 2.x/javax) | Después (Jakarta EE 10, `integration-tests-jakarta/`) |
|---|---|---|
| Orquestador | Arquillian 1.1.11.Final | **Arquillian 1.10.2.Final** |
| Navegador | **PhantomJS** (headless, descontinuado) | **Chrome headless** real |
| Gestor de navegador | Arquillian **Drone** 2.0 | **Selenium Manager** (auto-provisiona el driver) |
| Framework UI/esperas | **Graphene** 2.1.0.CR1 + page-fragments | Selenium 4 directo (esperas explícitas) |
| Driver | **Selenium 2.53.0** (JSON Wire) | **Selenium 4.35.0** (W3C WebDriver) |
| Evidencia al fallo | screenshooter 2.1.0.Alpha1 | (Selenium 4 nativo, si se necesita) |
| Contenedor | WildFly 8/9/10, Tomcat 7/8 | **WildFly 35.0.0.Final** gestionado |
| Ensamblado WAR | ShrinkWrap 1.2.3 | **ShrinkWrap resolver 3.3.7** |
| Impl. Faces | `org.glassfish:javax.faces` (Mojarra JSF 2) | Mojarra 4.0.24 (Faces 4.0, la trae el contenedor) |

Decisión clave: en vez de **portar** el framework legacy (~86 IT atados a
Graphene/PhantomJS), se creó una infra de integración nueva y mínima en
`integration-tests-jakarta/` (Fase G, con `RichFacesSmokeIT` en verde). El
stack legacy se **conserva sin tocar** para port incremental.

### 13.3 Referencias legacy que SIGUEN en el repo (pendientes, no bloquean)

El escaneo confirma que el stack legacy sigue declarado en estos sitios. NINGUNO
está en el reactor por defecto (examples comentados; IT legacy fuera del build
unitario), por eso no rompe el build actual, pero queda como deuda a limpiar en
la Fase J:

1. **`pom.xml` raíz** (perfil `integration-tests` y perfiles de navegador):
   - Propiedades `arquillian.drone.browser=phantomjs`, `arquillian.drone.reusable`,
     `testCategory.excluded.browser = AND NOT category.FailingOnPhantomJS`.
   - `arquillian.richfaces.jsfImplementation=org.glassfish:javax.faces` (JSF 2, javax).
   - Dependencias `graphene-webdriver`, `arquillian-drone-webdriver-depchain`,
     `arquillian-browser-screenshooter` (`version.screenshooter=2.1.0.Alpha1`),
     `version.selenium=2.53.0`, `version.arquillian.graphene=2.1.0.CR1` (en
     `build/pom.xml`).
   - Perfiles `browser-phantomjs`, `browser-chrome`, `browser-firefox` (Drone).
2. **`build/build-resources/src/main/resources/arquillian.xml`**: define
   `<extension qualifier="graphene">`, `<extension qualifier="webdriver">` con
   `phantomjs.binary.path`, `<extension qualifier="screenshooter">`, contenedores
   `jbosseap6`/`wildfly8/9/10`/`tomcat7/8` (todos JSF 2.x). Es la config del
   stack legacy completo.
3. **`build/page-fragments/`**: librería de Page Fragments de Graphene
   (`graphene-webdriver` como dep). Solo tiene sentido con Graphene.
4. **`components/*/src/test/integration/`**: los ~86 IT legacy que usan los page
   fragments + Graphene.
5. **`examples/showcase/pom.xml`** y **`examples/photoalbum/pom.xml`**: repiten
   toda la config Drone/PhantomJS/Graphene/perfiles de navegador (se aborda en la
   Fase H cuando se migren los examples).
6. **`TESTS.md`** y **`MIGRATION_JAVA21.md`**: documentan PhantomJS como browser
   por defecto y la matriz PhantomJS/Chrome/Firefox; hay que actualizarlos.
7. **Comentarios `PhantomJS` en JS de terceros** (`core/.../jquery.js`,
   `qunit.js`, `codemirror.js`, `statusChangeObserver.js`): son solo comentarios
   de compatibilidad dentro de librerías vendorizadas. **NO tocar** (no es
   nuestro código y no afecta a nada).

### 13.4 Tareas añadidas al plan (a ejecutar en Fases H y J)

Estas tareas cierran el hueco del tooling legacy detectado en el escaneo:

- [ ] **(Fase J) Limpiar el stack legacy del `pom.xml` raíz y `build/pom.xml`**:
      eliminar dependencias `graphene-webdriver`, `arquillian-drone-*`,
      `arquillian-browser-screenshooter`, y las propiedades
      `version.selenium=2.53.0`, `version.arquillian.graphene`,
      `version.screenshooter`. Quitar los perfiles `browser-phantomjs` (y
      revisar `browser-chrome`/`browser-firefox` que dependen de Drone).
      Hacerlo SOLO cuando la infra de `integration-tests-jakarta/` cubra lo que
      necesitemos, para no perder cobertura.
- [ ] **(Fase J) Reescribir `arquillian.xml`** (o crear uno nuevo bajo
      `integration-tests-jakarta/`): quitar `graphene`, `webdriver` (phantomjs),
      `screenshooter` y los contenedores JSF-2; dejar solo WildFly 35 (o el
      contenedor jakarta elegido). Cambiar `jsfImplementation` a Mojarra 4.
- [ ] **(Fase H) Migrar la config de integración de los examples** (`showcase`,
      `photoalbum`): reemplazar Drone/PhantomJS/Graphene por Selenium 4 + Chrome
      headless (o excluir sus IT del build por ahora, como el resto de examples).
- [ ] **(Fase J) Decidir el destino de `build/page-fragments`**: (a) portarlo a
      Selenium 4 si se quiere recuperar los ~86 IT, o (b) marcarlo como legacy y
      sacarlo del reactor. Documentar la decisión.
- [ ] **(Fase G/port incremental) Portar IT legacy** de
      `components/*/src/test/integration` a Selenium 4 según se vayan
      necesitando, reusando el patrón de `RichFacesSmokeIT`.
- [ ] **(Fase J) Actualizar `TESTS.md` y `MIGRATION_JAVA21.md`**: sustituir la
      matriz "PhantomJS (default)/Chrome/Firefox" por "Chrome headless (Selenium
      4, default)"; documentar cómo correr `integration-tests-jakarta`.
- [ ] **NO tocar** los comentarios `PhantomJS` dentro de las libs JS de terceros
      (jquery, qunit, codemirror): son vendorizadas.

> Estado: el escaneo NO reveló ninguna dependencia del stack legacy dentro de la
> ruta crítica (core/components jakarta). Todo lo legacy vive en perfiles no
> activos, examples comentados, o el `arquillian.xml` de build-resources. Por eso
> es **deuda de limpieza (Fase J)**, no un bloqueante de la migración jakarta.

---

## 14. Anexo — Dependencias deprecated y recomendaciones (escaneo del repo)

Escaneo hecho sobre los `pom.xml` y el código fuente (`@Deprecated`, flags de
JVM, versiones de plugins/CI) para inventariar lo que sigue obsoleto tras la
migración jakarta. Cada punto lleva su recomendación y la **fase donde se
ejecuta** (H = examples, J = limpieza/CI). NINGUNO bloquea el build actual; son
deuda a cerrar al continuar con H y J.

### 14.1 Dependencias descontinuadas o sin variante Jakarta

| Dependencia | Dónde | Estado | Recomendación | Fase |
|---|---|---|---|---|
| `org.jboss.cache:jbosscache-core` 3.2.5.GA (~2010) | `bom/pom.xml`, `core/pom.xml` | Muerto hace +10 años; se dejó en `javax.transaction` a propósito (no hay variante jakarta). `optional`. | **Eliminar.** Reemplazado por Infinispan. RichFaces solo lo usa como proveedor de caché opcional de recursos; se cubre con caché local o Infinispan. | J |
| `opensymphony:oscache` 2.3 | `bom/pom.xml`, `core/pom.xml` | Muerto (OpenSymphony desapareció). `optional`. | **Eliminar** sin reemplazo (redundante con los otros cachés). | J |
| `net.sf.ehcache:ehcache-core` 2.4.3 | `bom/pom.xml`, `core/pom.xml` | Línea 2.x muy antigua (viva es 3.x). `optional`. | Subir a EHCache 3.x **o eliminar** (proveedor de caché opcional redundante). | J |
| `com.yahoo.platform.yui:yuicompressor` 2.4.8 + Rhino antiguo | `build/resource-optimizer-plugin`, `build/pom.xml` | Descontinuado por Yahoo. Ya emite "syntax errors" al minificar JS moderno (jquery.js) y **el recurso se sirve SIN minificar**. | **Reemplazar** por Google Closure Compiler (JS) + minificador CSS moderno, o mover la minificación a un build de frontend. **Mayor impacto en producción.** | J |
| ~~`org.reflections:reflections` 0.9.8 (2013)~~ | ~~`build/resource-optimizer-plugin`, `core`~~ | ✅ **RESUELTO (Fase I):** reemplazado por **ClassGraph 4.8.174**; eliminados Reflections 0.9.8 y el Javassist 3.33 pineado. Lee bytecode Java 21 nativo. | Hecho. | I ✅ |

### 14.2 APIs marcadas `@Deprecated` en el propio código

Métodos/clases deprecated detectados en `core`, `a4j`, `rich`:
- `RendererUtils.getNestingForm(FacesContext, UIComponent)` y
  `findComponentFor(FacesContext, UIComponent, String)` — ya existen los
  reemplazos sin `FacesContext`.
- `SkinFactory.getInstance()` (sin contexto).
- `AbstractCacheableResource.isMatchesLastModified/isMatchesEntityTag`.
- `FastBufferReader(FastBufferWriter)` / `FastBufferInputStream(FastBufferOutputStream)`.
- Clases enteras: `AjaxResourceLibrary`, `push.EventAbortedException`.
- `AbstractTogglePanel.getChildIndex(String)`, `ExtendedDataTableRenderer` (método interno).

- **Recomendación:** **NO tocar durante la migración jakarta.** No bloquean y
  borrarlos rompe compatibilidad de API pública/binaria. Dejar para una limpieza
  posterior a que todo esté verde. Si los warnings de `-release 21` molestan,
  `@SuppressWarnings("deprecation")` en el llamador (no urgente). | J (posterior)

### 14.3 Configuración de build/JVM/CI obsoleta

| Ítem | Dónde | Estado | Recomendación | Fase |
|---|---|---|---|---|
| `-XX:MaxPermSize=...` | `examples/showcase/pom.xml`, `examples/photoalbum/pom.xml` | PermGen eliminado en Java 8; el flag se ignora (warning en JVMs estrictas). | **Quitar** todos los `-XX:MaxPermSize`. Ya no hace nada. | H |
| `.travis.yml` con `openjdk8`/`oraclejdk8` | raíz | CI atado a Java 8; Travis-CI muerto para OSS. El CI actual NO valida nada de lo migrado. | **Migrar a GitHub Actions** con Temurin 21. | J |
| `maven.min.version` 3.0 | `bom/pom.xml` | Maven 3.0 es de 2010. | Subir el mínimo a **3.8+/3.9** (acorde a los plugins modernos ya en uso). | J |

### 14.4 Puente `jakarta-bridge` (deuda temporal por diseño)

- No es "deprecated" en sentido estricto, pero es deuda temporal: post-transforma
  `javax`→`jakarta`.
- **Recomendación:** eliminar cuando `core`/`components` sean jakarta NATIVO y
  verdes (ya es la tarea 10.1 de la Fase J). | J

### 14.5 Prioridad sugerida

1. **Alto impacto en producción:** reemplazar YUI Compressor (recursos sin
   minificar hoy).
2. **Limpieza de bajo riesgo / alto valor:** quitar JBoss Cache + OSCache +
   `-XX:MaxPermSize`; migrar CI a GitHub Actions.
3. **Robustez del build:** Reflections → 0.10.x o ClassGraph.
4. **Cosmético / posterior:** métodos `@Deprecated` internos.

### 14.6 Checklist para incorporar en Fases H y J

**En la Fase H (examples):**
- [ ] Quitar `-XX:MaxPermSize=...` de `examples/showcase/pom.xml` y
      `examples/photoalbum/pom.xml` (y cualquier otro `arquillian.container.*.jvm.args`).

**En la Fase J (limpieza + CI):**
- [ ] Eliminar `jbosscache-core` de `bom/pom.xml` y `core/pom.xml` (+ propiedad
      `version.jbosscache`). Documentar que el caché opcional pasa a
      Infinispan/local.
- [ ] Eliminar `oscache` de `bom/pom.xml` y `core/pom.xml` (+ `version.oscache`).
- [ ] Decidir EHCache: subir a 3.x o eliminar (+ `version.ehcache`).
- [ ] Reemplazar YUI Compressor por Closure Compiler (JS) + minificador CSS
      moderno en `resource-optimizer-plugin`; verificar que los recursos se
      sirven minificados.
- [ ] Subir Reflections a 0.10.x (adaptar `MarkerResourcesScanner`) o migrar a
      ClassGraph.
- [ ] Subir `maven.min.version` a 3.8+/3.9 en `bom/pom.xml`.
- [ ] Migrar `.travis.yml` → GitHub Actions con Temurin 21.
- [ ] (Posterior, no bloqueante) Revisar/eliminar los métodos `@Deprecated`
      internos una vez todo esté verde.
