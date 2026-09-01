# Errores y Fixes — Migración RichFaces a Jakarta EE 10 (Faces 4.0, JDK 21, Liberty)

Este documento explica, en lenguaje llano, **por qué** la migración necesitó tantos
cambios y ajustes, qué es Mojarra, por qué la URL no termina en `.jsf`, y el
detalle de cada error que apareció al desplegar el mini-proyecto en Open Liberty
y cómo se resolvió.

---

## 0. El porqué de fondo: `javax.*` → `jakarta.*`

RichFaces 4.6.2 se escribió para **Java EE** (Oracle), donde todas las APIs vivían
bajo el paquete `javax.*` (`javax.faces`, `javax.servlet`, `javax.xml.bind`…).

En 2019 Oracle donó Java EE a la Eclipse Foundation y nació **Jakarta EE**. Por un
tema de marca/legal, Eclipse **no podía seguir usando el paquete `javax`**, así que
en Jakarta EE 9 se renombró TODO a `jakarta.*`. Esto no es un cambio cosmético: es
un cambio de nombre de paquete en cientos de clases. Una aplicación `javax` **no
arranca** en un servidor Jakarta y viceversa: son binariamente incompatibles.

Consecuencia directa: para que RichFaces corra en un servidor moderno (2024+) sobre
JDK 21, hay que reescribir cada `import javax.faces...` a `import jakarta.faces...`,
cada descriptor XML, cada referencia a recursos, etc. De ahí el gran volumen de
cambios. La mayor parte ya se hizo en fases anteriores (A–I); los que documentamos
aquí son los que **solo se destaparon al ejecutar la app de verdad** en Liberty.

> Regla mental: casi todos los fixes de abajo son variantes del mismo patrón —
> "algo seguía apuntando al mundo viejo `javax`/JSF 2 y hubo que apuntarlo al mundo
> nuevo `jakarta`/Faces 4".

---

## 1. ¿Qué es Mojarra? ¿Y MyFaces?

**Jakarta Faces** (antes "JSF", JavaServer Faces) es solo una **especificación**: un
documento que dice cómo debe comportarse el framework de vistas. Para usarlo hace
falta una **implementación** concreta. Existen dos:

- **Mojarra**: la implementación de referencia (la "oficial"). Es la que usaban
  JBoss/WildFly y con la que RichFaces se desarrolló y probó históricamente.
- **Apache MyFaces**: implementación alternativa de Apache. Es la que **Open Liberty
  trae de serie** con la feature `faces-4.0`.

Son intercambiables en teoría (misma spec), pero en la práctica tienen diferencias
internas. RichFaces registra clases propias (listeners, renderers) vía sus ficheros
`faces-config.xml`, y **MyFaces dentro de Liberty no conseguía cargar esas clases**
(ver error 5). Por eso cambiamos a **Mojarra empaquetada dentro del WAR**, usando la
feature `facesContainer-4.0` de Liberty ("trae tu propia implementación de Faces").

---

## 2. ¿Por qué la URL no es `.jsf`?

Históricamente muchas apps JSF se accedían por `/pagina.jsf` o `/pagina.faces`.
Eso **no es una regla del framework**, es solo cómo cada quien configura el mapeo
del *FacesServlet* en `web.xml`. En este proyecto el mapeo es:

```xml
<servlet-mapping>
    <servlet-name>Faces Servlet</servlet-name>
    <url-pattern>*.xhtml</url-pattern>
</servlet-mapping>
```

Es decir, las vistas se sirven con su extensión real **`.xhtml`** →
`http://localhost:9080/mini/index.xhtml`. Es el estilo moderno recomendado (la vista
y la URL coinciden, no hay extensión "mágica").

Si quisieras que respondiera también en `/mini/index.jsf`, bastaría añadir otro
`url-pattern`:

```xml
<servlet-mapping>
    <servlet-name>Faces Servlet</servlet-name>
    <url-pattern>*.xhtml</url-pattern>
    <url-pattern>*.jsf</url-pattern>   <!-- opcional, estilo antiguo -->
</servlet-mapping>
```

No es necesario ni recomendable; `.xhtml` funciona perfectamente.

---

## 3. Los errores que aparecieron al desplegar (y su fix)

Cada error de abajo apareció **en orden**: al arreglar uno, el siguiente quedaba al
descubierto. Esto es normal en una migración: cada capa que empieza a funcionar deja
ver el problema de la capa siguiente.

### Error 1 — POM inválido de `richfaces-core`: faltaban las dependencias en el WAR
**Síntoma:** al construir el WAR, Maven avisaba
`'dependencies.dependency.version' for io.github.classgraph:classgraph is missing`
y las librerías transitivas (guava, cssparser…) no llegaban al WAR.

**Causa:** la dependencia `classgraph` (usada por el optimizador de recursos) se
declaró **sin número de versión**, confiando en que un "gestor de versiones"
(`dependencyManagement`) la rellenara. Pero ese gestor estaba en un POM que **no
era el padre** de `core`, así que la versión nunca se resolvía y el POM publicado
quedaba inválido. Un POM inválido rompe la resolución de sus dependencias.

**Fix:** poner la versión explícita en `core/pom.xml`:
```xml
<version.classgraph>4.8.174</version.classgraph>
...
<dependency>
    <groupId>io.github.classgraph</groupId>
    <artifactId>classgraph</artifactId>
    <version>${version.classgraph}</version>
    <optional>true</optional>
</dependency>
```
(Sigue siendo `optional` → solo se usa al construir, no se empaqueta en el WAR. Correcto.)

---

### Error 2 — `NoClassDefFoundError: javax/xml/bind/JAXB`
**Síntoma:** la app arrancaba pero fallaba al iniciar RichFaces:
`org.richfaces.javascript.ClientServiceConfigParser` no encontraba `javax.xml.bind.JAXB`.

**Causa:** **JAXB** (Jakarta XML Binding, para leer/escribir XML) también se renombró
de `javax.xml.bind` a `jakarta.xml.bind`. Además, JAXB fue **eliminado del propio
JDK** en Java 11, así que ya ni siquiera está "gratis" en la plataforma: hay que
añadirlo como dependencia. Seis ficheros de RichFaces seguían importando el paquete
viejo.

**Fix:**
1. Migrar los `import javax.xml.bind.*` → `jakarta.xml.bind.*` en 6 clases
   (`ClientServiceConfigParser`, `validator/model/{ClientSideScripts, Component,
   Resource}` y 2 de test).
2. Empaquetar el runtime de JAXB en el WAR (`org.glassfish.jaxb:jaxb-runtime:4.0.5`),
   porque el JDK ya no lo trae y Liberty tampoco lo aporta por defecto.

---

### Error 3 — El JAR de RichFaces se instalaba **sin las clases** (solo recursos)
**Síntoma:** el `richfaces-5.0.0.jar` publicado tenía solo 11 entradas y ninguna
clase `.class`; en runtime, Mojarra leía el `faces-config.xml` del JAR pero no
encontraba las clases (`CollapsibleSubTableRenderer`, `DataTablePreRenderListener`,
`ListHandler`…).

**Causa:** RichFaces tiene un **optimizador de recursos** que, durante el build,
minifica el JavaScript (jQuery, atmosphere…) con una herramienta antigua
(**YUI Compressor + Rhino**). Ese minificador **no entiende el JavaScript moderno** y
lanzaba una excepción. Al intentar evitar el problema con `-Doptimization.skip=true`,
se rompía el orden de empaquetado y el JAR salía **sin las clases compiladas**.

**Fix:** hacer que el fallo de minificación **no sea fatal**. Se modificó
`JavaScriptCompressingProcessor` para que, si la compresión falla, **escriba el JS sin
minificar** (fallback) en vez de abortar. Así el build completo (con el optimizador)
termina bien y el JAR sale **completo, con sus ~800 clases** y de forma determinista.
(El recurso se sirve sin minificar; funcionalmente idéntico, solo un poco más grande.)

> Detalle de proceso: el JAR bueno se reconstruyó desde `target/classes` (que sí
> tenía todas las clases) para garantizar un artefacto completo en el repositorio local.

---

### Error 4 — (relacionado con el 3) El minificador YUI/Rhino no parsea JS moderno
**Síntoma:** `EvaluatorException: Compilation produced 10 syntax errors` sobre
`jquery.js`, y a veces faltaba `Compressed/.../atmosphere.js`, rompiendo el
empaquetado del JAR de forma intermitente.

**Causa:** YUI Compressor 2.4.8 usa el parser Rhino de ~2013; el jQuery/atmosphere
actuales usan sintaxis ECMAScript que ese parser no reconoce.

**Fix:** el mismo del Error 3 (fallback a "sin minificar"). Actualizar el minificador
a uno moderno queda como mejora futura (deuda documentada), pero no bloquea nada: los
recursos se sirven igual.

---

### Error 5 — `ClassNotFoundException: DataTablePreRenderListener` con MyFaces
**Síntoma:** con la feature `faces-4.0` de Liberty (que usa **MyFaces**), la app
arrancaba pero MyFaces no podía instanciar los listeners/renderers que RichFaces
declara en sus `faces-config.xml`.

**Causa:** MyFaces viene **empaquetado en un bundle interno de Liberty (OSGi)**. Su
cargador de clases (classloader) **no ve las clases del WAR** de la aplicación. Cuando
MyFaces intenta crear `org.richfaces.event.DataTablePreRenderListener` (que está en el
WAR), no la encuentra. Es un choque estructural entre RichFaces y la forma en que
Liberty embebe MyFaces.

**Fix:** dejar de usar la MyFaces de Liberty y **traer Mojarra dentro del WAR**:
- En `server.xml`: cambiar la feature `faces-4.0` por **`facesContainer-4.0`**
  (modo "trae tu propia implementación de Faces").
- En el `pom.xml` del proyecto: añadir **Mojarra** (`org.glassfish:jakarta.faces:4.0.24`)
  como dependencia `compile` (se empaqueta en el WAR).

Ahora Mojarra se carga con el **classloader de la aplicación**, que sí ve las clases
de RichFaces. Además, RichFaces 4.x está diseñado y probado con Mojarra. Problema
resuelto de raíz.

---

### Error 6 — `Unable to find resource javax.faces, jsf.js`
**Síntoma:** la página cargaba, pero salía ese mensaje y el Ajax no tenía su script.

**Causa:** en JSF 2 el script de Ajax del framework se llamaba **`jsf.js`** y vivía en
la librería **`javax.faces`**. En **Faces 4.0** se renombró a **`faces.js`** en la
librería **`jakarta.faces`**. RichFaces pedía el nombre viejo, que ya no existe en
Mojarra 4.

**Fix:** migrar todas las declaraciones de dependencia de recurso de
`library = "javax.faces", name = "jsf.js"` a
`library = "jakarta.faces", name = "faces.js"` en los componentes de `a4j` y `rich`
(unas ~40 clases con `@ResourceDependency`, más un par con `ResourceKey.create(...)`).

---

### Error 7 — El puerto 9080 estaba ocupado (`Address already in use: bind`)
**Síntoma:** la app arrancaba correctamente pero Liberty no podía abrir el puerto
9080 y quitaba la aplicación (`Web application removed`).

**Causa:** habían quedado varios procesos `java.exe` **huérfanos** de intentos de
arranque anteriores, cada uno reteniendo el puerto.

**Fix:** matar los procesos Java colgados (o reiniciar la máquina, que fue lo que se
hizo) y arrancar Liberty limpio. No es un problema de la migración, sino de higiene
del entorno tras muchos reinicios.

---

### Error 8 — `ViewExpiredException: View /index.xhtml could not be restored`
**Síntoma:** al pulsar un botón tras redesplegar, salía esta excepción.

**Causa:** la pestaña del navegador tenía una vista **vieja** (con un identificador de
estado —"ViewState"— del despliegue anterior). Al redeployar, ese estado ya no existe
en el servidor.

**Fix:** recargar la página (**Ctrl+F5**) para obtener una vista nueva. No es un bug;
es el comportamiento normal de Faces cuando el servidor se reinicia con clientes
abiertos. (Opcional: guardar el estado en cliente con
`jakarta.faces.STATE_SAVING_METHOD=client` para que sobreviva a redeploys.)

---

### Error 9 — Los componentes salían **sin estilo** (skin no aplicado) → 404 en `skinning.ecss`
**Síntoma:** RichFaces funcionaba (Ajax OK) pero todo se veía como texto plano. La
hoja de estilo del skin (`skinning.ecss`) devolvía **404 Not Found**.

**Causa:** RichFaces sirve su CSS de "skinning", imágenes y JS bajo la ruta especial
`/org.richfaces.resources/*`, atendida por un servlet propio
(`org.richfaces.webapp.ResourceServlet`). Ese servlet **se auto-registra** mediante un
`ServletContainerInitializer` (un mecanismo de arranque de Servlet 3+). Bajo Liberty +
`facesContainer`, ese inicializador **no se ejecutó**, así que el servlet nunca se
registró y todas las URLs `/org.richfaces.resources/...` daban 404 → sin CSS → sin
estilo.

**Fix:** registrar el servlet **explícitamente** en el `web.xml` del proyecto:
```xml
<servlet>
    <servlet-name>Resource Servlet</servlet-name>
    <servlet-class>org.richfaces.webapp.ResourceServlet</servlet-class>
    <load-on-startup>1</load-on-startup>
</servlet>
<servlet-mapping>
    <servlet-name>Resource Servlet</servlet-name>
    <url-pattern>/org.richfaces.resources/*</url-pattern>
</servlet-mapping>
```
Con esto el `skinning.ecss` devuelve 200 (CSS real) y el skin (p. ej. `ruby`) pinta la
cabecera de color, las pestañas, los botones, etc.

---

## 4. Resumen: ¿por qué "tantos" cambios?

Todos los cambios caen en cuatro grupos, y casi todos son el **mismo problema de
fondo** (mundo viejo `javax`/JSF2 → mundo nuevo `jakarta`/Faces4):

| Grupo | Qué se arregló | Por qué |
|-------|----------------|---------|
| **Paquetes `javax` → `jakarta`** | imports JAXB, referencias a recursos `jsf.js`→`faces.js` | Jakarta EE renombró todos los paquetes; el JDK ya no trae JAXB |
| **Implementación de Faces** | MyFaces (Liberty) → Mojarra (en el WAR), feature `facesContainer-4.0` | El classloader OSGi de MyFaces en Liberty no ve las clases de RichFaces |
| **Build / empaquetado** | versión de classgraph en el POM, fallback del minificador JS | POMs con versiones no gestionadas + minificador antiguo que no lee JS moderno |
| **Configuración de despliegue** | registrar `ResourceServlet` en `web.xml`, puerto libre, recargar vista | El auto-registro de servlets no corre en Liberty+facesContainer |

En una frase: **RichFaces 4.6 nació para un mundo (Java EE 6/JSF 2, servidores tipo
JBoss con Mojarra) y lo estamos haciendo correr en otro (Jakarta EE 10/Faces 4, JDK
21, Open Liberty)**. Cada pieza que asumía el mundo viejo tuvo que reapuntarse al
nuevo, y varias de esas piezas solo se ven cuando la aplicación corre de verdad, no
al compilar.

---

## 5. Estado final verificado

- Mini-proyecto WAR (Java 21) desplegado en **Open Liberty 24** con
  **`facesContainer-4.0` + Mojarra 4.0.24 + CDI 4.0 + Servlet 6.0**.
- `rich:panel`, `rich:tabPanel`, `rich:progressBar`, `rich:inplaceInput` renderizan
  con el **skin `ruby`** aplicado.
- El **Ajax de `a4j:commandButton`** funciona (incrementa el contador y actualiza solo
  las zonas indicadas, sin recargar la página).
- URL: `http://localhost:9080/mini/index.xhtml`.
