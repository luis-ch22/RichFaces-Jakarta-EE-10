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
- [ ] Definir propiedades nuevas en `pom.xml` raíz y `build/pom.xml`:
  - `version.jakarta.bom=10.0.0` (jakarta.platform:jakarta.jakartaee-bom).
  - `version.mojarra=4.0.x` (org.glassfish:jakarta.faces).
  - `version.jaxb=4.0.x` (jakarta.xml.bind-api + glassfish jaxb-runtime).
  - Mantener temporalmente `version.jboss-javaee` hasta migrar cada módulo.

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
- [ ] `faces-config.xml`: subir a esquema **Faces 4.0**
      (`https://jakarta.ee/xml/ns/jakartaee`, `version="4.0"`).
- [ ] `*.taglib.xml`: namespace de taglib jakarta
      (`http://jakarta.faces...` según spec 4.0). Ojo: los taglibs de RichFaces
      declaran su propio namespace de librería; lo que cambia es el esquema y
      referencias a facelets/faces jakarta.
- [ ] Namespaces XML de vistas (`.xhtml` de examples): de
      `http://xmlns.jcp.org/jsf/...` / `http://java.sun.com/...` a
      `jakarta.faces.*`.

### 3.4 Compilar y estabilizar
- [ ] `mvn -pl core -am -Dmaven.test.skip=true -Dgpg.skip=true clean install`.
- [ ] Corregir errores residuales de API (métodos renombrados/eliminados en
      Faces 4.0 respecto a 2.x). El `core` NO usa managed beans, así que el
      grueso debería ser renombrado mecánico.

---

## 4. Fase D — CDK: que genere `jakarta.*` (usando el CDK 10.0.1 jakarta)

Sin esto, `components` (a4j/rich) volvería a generar renderers/componentes con
imports `javax.faces` y no compilaría contra Faces 4.0. Ya NO es el punto de
mayor riesgo: existe un **CDK jakarta publicado** (`10.0.1`, ver sección 0).

### 4.1 Vía D-1 — Adoptar el CDK 10.0.1 jakarta de albfernandez (preferente)
- [ ] Cambiar en `pom.xml` raíz y `build/pom.xml` las coordenadas del CDK:
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

## 5. Fase E — faces-config / taglibs / registro CDI de la librería

- [ ] Revisar `core/src/main/resources/META-INF/core.faces-config.xml`: el
      managed-bean `a4jSkin` (scope application) es un `managed-bean` clásico de
      JSF. En Faces 4.0 los managed-beans fueron eliminados. Opciones:
  - Convertir `SkinBean` a CDI (`@Named("a4jSkin")` +
    `@jakarta.enterprise.context.ApplicationScoped`) y añadir `beans.xml`.
  - O registrarlo vía `application`/`ELResolver` programático
    (`SkinPropertiesELResolver` ya existe; evaluar si basta).
  - **Impacto de skinning:** `#{a4jSkin.xxx}` en los `.ecss` debe seguir
    resolviendo. Verificar tras el cambio que el CSS compilado
    (`CompiledCSSResource`) sigue sustituyendo variables.
- [ ] Añadir `beans.xml` (CDI 4.0, `bean-discovery-mode="annotated"`) donde haga
      falta activar CDI en la librería.
- [ ] Buscar otros `managed-bean` en los `*.faces-config.xml` de `core` y
      `components` y migrarlos igual.

---

## 6. Fase F — Tests de la librería en Jakarta

### 6.1 Sustituir los mocks JSF (`com.github.albfernandez.test-jsf`)
Estos mocks están atados a `javax.faces`. Para tests jakarta:
- [ ] Buscar si existe una variante jakarta del `test-jsf` de albfernandez
      (mismo groupId con clasificador/versión jakarta). Si existe, cambiar
      `version.jsf-test` y coordenadas en `build/pom.xml`.
- [ ] Si no existe: evaluar reemplazar por Mojarra 4 + un mock ligero, o por
      `org.jboss.test.faces` en versión jakarta, o desactivar temporalmente los
      tests que dependen de los mocks y cubrir con tests de integración
      (Arquillian) en Fase G.
- [ ] Recordar el `--add-opens` de surefire (ya en `pom.xml` raíz) para cglib.

### 6.2 Deuda: tests de `rich` que no compilan
- [ ] Aislar el punto de fallo de la cascada `@Override does not override...`
      (p. ej. `AbstractAccordionTest`). Hipótesis del doc previo: un único punto
      de resolución de tipos en la jerarquía generada por el CDK que rompe en
      cascada, o tests que asumen atributos que el CDK actual no genera.
- [ ] Tras arreglar el CDK (Fase D) es probable que muchos de estos errores
      desaparezcan (las clases base generadas cambian). Reintentar
      `mvn -pl components/rich test` y arreglar los residuales.

### 6.3 Ejecutar unitarios
- [ ] `mvn -pl core test` y `mvn -pl components/a4j test` deben quedar verdes
      (238 y 119 respectivamente en la Fase 21; mantener ese baseline).
- [ ] `mvn -pl components/rich test` objetivo: verde (era la deuda).

---

## 7. Fase G — Integración con contenedor Jakarta EE 10

- [ ] Actualizar el stack de integración en `build/*`:
  - **Arquillian** a versión compatible con Jakarta EE 10.
  - **Reemplazar PhantomJS** (descontinuado) por Selenium moderno + Chrome
    headless (hay Chrome 151 en la máquina, ver `MIGRATION_JAVA21.md`).
  - **Contenedores**: WildFly 30+ (Jakarta EE 10) y/o Tomcat 10.1+ (Servlet 6).
    Actualizar las propiedades `version.wildfly*` / `version.tomcat*` del
    `pom.xml` raíz por versiones jakarta.
- [ ] Ejecutar al menos un perfil de integración:
      `mvn verify -P<perfil-wildfly-jakarta>`.

---

## 8. Fase H — Examples a Jakarta (incluye migración CDI real)

Los examples SÍ usan managed beans JSF (`javax.faces.bean.*`), eliminados en
Faces 4.0. Aquí está el refactor CDI de verdad. Alcance conocido (verificado):
`examples/template`, `examples/standalone-js`, `examples/showcase` (el más
grande, ~30 beans), `examples/push-demo`.

- [ ] Renombrado jakarta (Fase C) sobre cada example.
- [ ] Migrar anotaciones de managed bean a CDI:
  - `@javax.faces.bean.ManagedBean` → `@jakarta.inject.Named`.
  - `@RequestScoped` → `jakarta.enterprise.context.RequestScoped`.
  - `@SessionScoped` → `jakarta.enterprise.context.SessionScoped` (bean
    `Serializable`).
  - `@ApplicationScoped` → `jakarta.enterprise.context.ApplicationScoped`.
  - `@ViewScoped` → `jakarta.faces.view.ViewScoped` (sigue existiendo en Faces).
  - `@ManagedProperty` → `@Inject` + `@Named` (inyección CDI).
- [ ] Añadir `beans.xml` a cada example (`WEB-INF/beans.xml`, CDI 4.0).
- [ ] `examples/photoalbum`: además usa `javax.activation` (JAF, removido del
      JDK) — ya se le añadió `javax.activation:1.2.0` en la Fase 21; en jakarta
      pasa a `jakarta.activation:jakarta.activation-api:2.1`.
- [ ] `examples/showcase`: `javax.xml.bind` (JAXB) en `CapitalsParser`,
      `CDParser` → `jakarta.xml.bind`.
- [ ] Subir `source/target 1.7` restantes de examples a `release=21` (algunos
      ya se hicieron en la Fase 21; verificar).
- [ ] Construir cada example bajo demanda (siguen comentados en el reactor):
      `mvn -DskipTests -Dgpg.skip=true -f examples/<x>/pom.xml clean package`.

---

## 9. Fase I — Deuda del resource-optimizer (build reproducible)

- [ ] Subir `reflections` de 0.9.8 a 0.10.x (o 0.9.12) para que lea bytecode
      Java 21 de forma fiable.
- [ ] ADAPTAR el scanner custom
      `core/src/main/resource-optimizer/.../MarkerResourcesScanner` a la nueva
      API (`scan(Object)` cambió a `scan(Object, Store)` en 0.9.12; puede diferir
      más en 0.10.x). Alternativa: reemplazar el motor de escaneo.
- [ ] Verificar que los goals `packed-resources` /
      `packed-compressed-resources` de `rich` pasan de forma determinista.
- [ ] (Opcional) Actualizar YUI Compressor 2.4.8 + Rhino antiguo, que emite
      "syntax errors" al minificar JS moderno (no fatal hoy).

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
