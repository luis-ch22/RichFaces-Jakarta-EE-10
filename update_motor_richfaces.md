# Modernizar el estilo de RichFaces (look & feel tipo Angular Material)

Este documento recoge cómo funciona el estilado en RichFaces, qué enfoques
existen para darle un aspecto moderno (Material-like), y qué habría que
programar en el `core` para que RichFaces **genere skins modernas de forma
nativa**. Todo está basado en el código real del proyecto (módulos `core` y
`components`), no en teoría general.

---

## 1. Cómo funciona el estilado en RichFaces

RichFaces usa un sistema llamado **skinning**. No escribes CSS directamente
sobre los componentes; defines un archivo `.skin.properties` con variables
(colores, fuentes, bordes) y RichFaces genera el CSS a partir de plantillas.

Trae skins de fábrica como `blueSky`, `emeraldTown`, `deepMarine`, `wine`,
`japanCherry`, `ruby`, `classic`, `plain`. Se selecciona en `web.xml` con el
parámetro `org.richfaces.skin`.

Los componentes se renderizan como **HTML del lado servidor** con clases CSS
fijas (prefijadas `rf-`, por ejemplo `rf-p` para panel, `rf-cb` para command
button). Ese es el punto de enganche para restilizarlos.

---

## 2. Opciones para lograr un look moderno (Material-like)

### Opción A — Skin propio (la vía "nativa" de RichFaces)

Creas un `.skin.properties` con una paleta Material (primario, acento,
superficies, tipografía Roboto) y lo activas en `web.xml`.

- **Ventaja:** es el mecanismo soportado y afecta a todos los componentes de
  forma coherente.
- **Limitación (en su forma básica):** el skinning controla colores/fuentes/
  bordes, pero por defecto no da elevación (sombras en capas), ripple, ni el
  layout redondeado característico de Material. Te acerca en paleta, no en
  "sensación".

> **Matiz importante** (ver sección 5): el motor de skinning es en realidad más
> potente de lo que sugiere esta limitación. Con pequeñas extensiones SÍ puede
> generar radios, sombras y transiciones de forma nativa.

### Opción B — CSS override moderno encima (lo más práctico)

Dejas un skin neutro (`plain`) y encima cargas una hoja de estilos propia que
redefine las clases `rf-*` con estética Material: `border-radius`, `box-shadow`
para elevación, colores primarios/acento, tipografía Roboto, transiciones.
Puedes incluso importar variables/tokens de Material Design (los CSS custom
properties) y mapearlos a las clases `rf-*`.

- **Ventaja:** control total del aspecto sin pelear con el generador de skins.
  Es el enfoque recomendado para "verse moderno" rápido.

### Opción C — Combinar ambos

Skin propio para la paleta base + una capa CSS override para sombras, radios y
micro-interacciones. Es el mejor equilibrio calidad/esfuerzo.

### Lo que NO es realista

Meter **Angular Material de verdad** (sus componentes Angular) no encaja:
RichFaces renderiza HTML en el servidor con JSF, mientras Angular Material son
componentes de un framework SPA cliente. Son arquitecturas incompatibles en el
mismo árbol de componentes. Lo alcanzable es replicar el **lenguaje visual** de
Material (look & feel) sobre el HTML que RichFaces ya genera, no incrustar la
librería Angular.

### Flujo de trabajo concreto (enfoque CSS override / combinado)

1. Inventariar qué componentes RichFaces usa la app y sus clases `rf-*` reales
   (inspeccionando el HTML renderizado en el navegador, F12).
2. Definir tokens Material: paleta primaria/acento, superficies, tipografía,
   radios, elevaciones.
3. Elegir enfoque (recomendado B o C).
4. Escribir la hoja `material-richfaces.css` mapeando `rf-*` a los tokens,
   empezando por los componentes más visibles (paneles, botones, inputs, tablas,
   tabs).
5. Cargarla después del skin en la página maestra (o como recurso JSF) para que
   gane en cascada.
6. Iterar componente por componente comparando contra la referencia Material.

---

## 3. Enfoque B en detalle — CSS override moderno encima

La idea es dejar RichFaces con el skin `plain` (que tiene todas sus propiedades
en `#{null}`, o sea un lienzo limpio) y encima cargar tu propia hoja CSS que
redefine las clases `rf-*`.

**Por qué `plain` funciona bien de base:** al estar todo en `#{null}`, RichFaces
no inyecta colores ni bordes agresivos. Los componentes quedan "desnudos" y tu
CSS gana en cascada sin pelear contra estilos generados.

**Cómo se conecta.** En `web.xml`:

```xml
<context-param>
    <param-name>org.richfaces.skin</param-name>
    <param-value>plain</param-value>
</context-param>
```

Y en la página maestra (template `.xhtml`), cargas la hoja **después** de los
recursos de RichFaces para que gane la cascada:

```xml
<h:head>
    <h:outputStylesheet name="css/material-richfaces.css" />
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500&display=swap" rel="stylesheet"/>
</h:head>
```

**Cómo se ve la hoja `material-richfaces.css`.** Primero defines tokens Material
como variables CSS, luego mapeas las clases `rf-*` a esos tokens:

```css
/* 1. Tokens Material Design como custom properties */
:root {
  --md-primary: #6200ee;
  --md-primary-variant: #3700b3;
  --md-secondary: #03dac6;
  --md-surface: #ffffff;
  --md-on-surface: #1c1b1f;
  --md-outline: #79747e;
  --md-radius: 8px;
  --md-elevation-1: 0 1px 3px rgba(0,0,0,.12), 0 1px 2px rgba(0,0,0,.24);
  --md-elevation-2: 0 3px 6px rgba(0,0,0,.16), 0 3px 6px rgba(0,0,0,.23);
  --md-font: 'Roboto', 'Segoe UI', sans-serif;
}

body, .rf-p, .rf-cb {
  font-family: var(--md-font);
}

/* 2. Panel (rf-p) -> tarjeta Material con elevación */
.rf-p {
  background: var(--md-surface);
  border: none;
  border-radius: var(--md-radius);
  box-shadow: var(--md-elevation-1);
  transition: box-shadow .2s ease;
}
.rf-p:hover { box-shadow: var(--md-elevation-2); }
.rf-p-hdr {           /* cabecera del panel */
  background: transparent;
  color: var(--md-on-surface);
  font-weight: 500;
  border-bottom: 1px solid rgba(0,0,0,.08);
}

/* 3. Command button (rf-cb) -> botón Material contained */
.rf-cb {
  background: var(--md-primary);
  color: #fff;
  border: none;
  border-radius: var(--md-radius);
  padding: 0 16px; height: 36px;
  text-transform: uppercase;
  letter-spacing: .05em;
  box-shadow: var(--md-elevation-1);
  cursor: pointer;
  transition: background .2s ease, box-shadow .2s ease;
}
.rf-cb:hover { background: var(--md-primary-variant); box-shadow: var(--md-elevation-2); }
.rf-cb:active { box-shadow: none; }

/* 4. Inputs -> estilo Material outlined */
.rf-insp-inp, .rf-ii, input[type=text] {
  border: 1px solid var(--md-outline);
  border-radius: 4px;
  padding: 12px;
  transition: border-color .2s ease, box-shadow .2s ease;
}
.rf-insp-inp:focus, input[type=text]:focus {
  border-color: var(--md-primary);
  box-shadow: 0 0 0 2px rgba(98,0,238,.2);
  outline: none;
}
```

El truco está en conocer las clases reales `rf-*` de cada componente. Se sacan
inspeccionando el HTML renderizado (F12). Ejemplos: `rf-p` = panel, `rf-p-hdr` =
cabecera de panel, `rf-cb` = command button, `rf-tab` = pestaña, `rf-dt` =
dataTable.

- **Ventajas:** control total del look, incluidas sombras, radios, transiciones,
  hover, focus. Esto sí llega a "sensación Material" real. No dependes del
  generador de skins.
- **Contras:** trabajo manual componente por componente, y si actualizas
  RichFaces y cambian nombres de clases, hay que ajustar. En este proyecto la
  versión está congelada, así que no es problema.

---

## 4. Enfoque A en detalle — Skin propio nativo (qué puede y qué no, forma básica)

Un `.skin.properties` es un archivo de **pares clave=valor**. No es CSS ni
JavaScript: es una tabla de variables que RichFaces inyecta en sus plantillas
para generar el CSS final.

**Qué controlas** (usando `emeraldTown` como referencia real):

- **Colores:** `headerBackgroundColor`, `generalBackgroundColor`,
  `controlBackgroundColor`, `panelBorderColor`, `tableHeaderBackgroundColor`,
  `generalLinkColor`, `hoverLinkColor`, etc.
- **Tipografía:** `generalFamilyFont`, `generalSizeFont`, `headerFamilyFont`,
  `buttonFamilyFont`, `headerWeightFont`.
- **Bordes:** `tableBorderColor`, `tableBorderWidth`.
- **Gradientes:** `gradientType` (valores tipo `plain`, `glass`, etc.).
- **Sombra básica:** `shadowBackgroundColor`, `shadowOpacity` (sombra simple del
  framework, no elevación Material real).
- **Colores de estado:** `warningColor`, `errorColor`, y bloques específicos
  como calendario o editor.

Ejemplo de skin Material básico:

```properties
generalFamilyFont=Roboto, 'Segoe UI', sans-serif
generalSizeFont=14px
headerBackgroundColor=#6200EE
headerGradientColor=#7F39FB
headerTextColor=#FFFFFF
headerWeightFont=500
controlBackgroundColor=#FFFFFF
panelBorderColor=#E0E0E0
generalLinkColor=#6200EE
hoverLinkColor=#3700B3
selectControlColor=#03DAC6
tableHeaderBackgroundColor=#6200EE
tableHeaderTextColor=#FFFFFF
gradientType=plain
```

Se coloca en `META-INF/skins/material.skin.properties` y se activa con
`org.richfaces.skin = material`.

**Limitaciones de la forma BÁSICA (rellenar solo las claves conocidas):**

- **Sin animaciones ni transiciones** si te limitas a las claves clásicas.
- **Sin `border-radius`** vía las claves genéricas de color (aunque OJO: hay
  claves de radio específicas, ver sección 5).
- **Sin elevación Material** con la sombra clásica (`shadowBackgroundColor` /
  `shadowOpacity` es plana).
- **Sin control fino de spacing/padding** más allá de las claves predefinidas.

**Conclusión (forma básica):** te da la paleta y la tipografía Material de forma
coherente en todos los componentes, con poco esfuerzo. Pero se queda corto en
radios, elevación en capas, transiciones, hover animado y ripple.

> Esta conclusión aplica a **usar el skinning tal cual, sin tocar código**. La
> sección 5 muestra que el motor da para bastante más si se extiende.

---

## 5. La vía nativa avanzada: qué habría que programar para skins modernas

Investigando el motor de skinning del `core`, el hallazgo clave cambia la
respuesta: **RichFaces no genera CSS estático desde el `.skin.properties`. Tiene
un sistema de plantillas de estilo con variables EL.** No hay que inventarlo,
hay que ampliarlo.

### 5.1 Cómo funciona el motor por dentro (dos mitades unidas por EL)

1. **Modelo:** el `.skin.properties` se carga en un mapa y se expone como un
   bean EL llamado **`a4jSkin`**.
2. **Vista:** los estilos de los componentes NO son CSS normal, son plantillas
   **`.ecss`** ("Extended CSS") que contienen expresiones EL. Ejemplo real de
   `panel.ecss`:

   ```css
   .rf-p {
       background-color: '#{a4jSkin.generalBackgroundColor}';
       border-radius: '#{a4jSkin.panelBorderRadius}';
   }
   ```

Cuando el navegador pide el CSS, `CompiledCSSResource` parsea el `.ecss` con un
parser **CSS3 real** y `CSSVisitorImpl` evalúa cada `#{a4jSkin.xxx}`,
sustituyéndolo por el valor del skin, y emite CSS plano.

### 5.2 Hallazgo decisivo: las propiedades NO son fijas

El conjunto de propiedades de skin es **dinámico, no está cerrado**. La interfaz
`Skin.java` tiene constantes (`HEADER_BACKGROUND_COLOR`, etc.) pero son solo
documentación. El modelo real (`SkinImpl` guarda un `Map`, `SkinBean.get(key)`)
acepta **cualquier clave**.

Prueba en el propio código: las plantillas `.ecss` ya usan claves que **no**
existen como constantes en `Skin.java` (como `panelBorderRadius`,
`hoverLinkColor`).

> Si añades una clave nueva al `.skin.properties` y la referencias en un
> `.ecss`, funciona automáticamente. No hay que tocar el parser ni el factory.

Además, el skinning ya conoce el concepto de radio de esquina:
`BUTTON_RADIUS_CORNER = "buttonRadiusCorner"`,
`PANEL_RADIUS_CORNER = "panelRadiusCorner"`, `TAB_RADIUS_CORNER`. Los redondeos
ya son parte del vocabulario del skin.

### 5.3 Clases clave del motor (módulo `core`)

| Clase / archivo | Responsabilidad |
|---|---|
| `org.richfaces.skin.AbstractSkinFactory` | Localiza, carga y cachea los `.skin.properties` (rutas `META-INF/skins/%s.skin.properties` y `%s.skin.properties`, procesamiento de EL). |
| `org.richfaces.skin.SkinFactoryImpl` | Skin `DEFAULT`, resolución del skin actual, temas, composición `mainSkin` + `baseSkin`. |
| `org.richfaces.skin.SkinImpl` / `AbstractSkin` | Modelo resuelto: evalúa EL, herencia (`baseSkin`), referencias `&` entre propiedades, y conversión de color (`decodeColor` con `HtmlColor`). |
| `org.richfaces.skin.SkinBean` (+ registro `a4jSkin` en `core.faces-config.xml`) y `SkinPropertiesELResolver` | Exponen el skin a EL como `#{a4jSkin.xxx}`. `SkinBean` es un `AbstractMap`, por eso resuelve cualquier clave. |
| `org.richfaces.skin.Skin` (interfaz) | Catálogo canónico de nombres de propiedad (constantes) — **no restrictivo**. |
| `org.richfaces.resource.CompiledCSSResource` | Parsea el `.ecss` (CSSParser SAC / CSS3) y versiona el CSS por `skin.hashCode(context)` (invalidación de caché). |
| `org.richfaces.resource.css.CSSVisitorImpl` | Recorre el CSS DOM y **evalúa las expresiones EL** sustituyéndolas por valores del skin (`visitStyleDeclaration`). Punto exacto de sustitución. |
| `org.richfaces.resource.ResourceFactoryImpl` / `ResourceHandlerImpl` | Enrutan `.ecss` al pipeline compilado y fijan el renderer Stylesheet. |
| `org.richfaces.application.GlobalResourcesViewHandler` | Inyecta hojas de skinning global (`skinning.ecss`, `skinning_classes.ecss`). |

Ubicación de las plantillas `.ecss`:
- Componentes rich: `components/rich/src/main/resources/META-INF/resources/org.richfaces/*.ecss`
  (`panel.ecss`, `dropdownmenu.ecss`, `extendedDataTable.ecss`, etc.).
- Skinning global (core): `core/src/main/resources/META-INF/resources/org.richfaces/skinning.ecss` y `skinning_classes.ecss`.

### 5.4 Qué habría que programar — tres niveles de esfuerzo

#### Nivel 1 — Solo datos, cero código Java (lo más fácil)

Para colores, tipografía, radios y sombras **estáticas**, no hay que programar
nada en Java. Basta con:

1. Crear `core/src/main/resources/META-INF/skins/material.skin.properties` con
   claves nuevas:

   ```properties
   generalFamilyFont=Roboto, sans-serif
   headerBackgroundColor=#6200EE
   panelBorderRadius=8px
   panelBoxShadow=0 1px 3px rgba(0,0,0,.12), 0 1px 2px rgba(0,0,0,.24)
   controlTransition=all .2s ease
   ```

2. Editar los `.ecss` de los componentes para consumir esas claves:

   ```css
   .rf-p {
       border-radius: '#{a4jSkin.panelBorderRadius}';
       box-shadow: '#{a4jSkin.panelBoxShadow}';
       transition: '#{a4jSkin.controlTransition}';
   }
   ```

El parser es CSS3 real, así que sombras múltiples y `transition` compuesta
deberían parsear bien. **Punto a validar:** cómo maneja `CSSVisitorImpl` los
valores con comas y comillas anidadas. Con esto ya tienes elevación, redondeos y
transiciones **de forma nativa**, sujetas al skin activo.

#### Nivel 2 — Funciones EL de color (para que sea "inteligente")

Esto hoy **no existe** y habría que programarlo. No hay funciones tipo
`darken()`, `lighten()`, `rgba()` o mezcla de colores accesibles desde los
`.ecss`. Sin ellas, cada color derivado (hover, sombra semitransparente,
variante del primario) se precalcula a mano en el `.properties`.

Lo bueno: la maquinaria de color ya está en Java (`AbstractSkin.decodeColor`
usa `HtmlColor`). Dos vías para exponer funciones a las plantillas:

- **Opción A** — añadir métodos de utilidad a un bean EL, igual que ya existe
  `#{a4jSkin.imageUrl('x.png')}`. Ejemplo: `#{a4jSkin.darken('headerBackgroundColor', 10)}`.
- **Opción B** — registrar una taglib de funciones EL propia y llamarla desde el
  `.ecss`: `#{rfSkinFn:rgba(a4jSkin.primaryColor, 0.2)}`.

Con esto defines solo el color primario y las variantes (hover, presionado,
sombras, ripple base) se derivan solas, como en un theme Material real.

#### Nivel 3 — Motor de theming completo (lo ambicioso)

Un sistema de temas Material real (paletas primaria/secundaria con tonos
50–900, modo claro/oscuro, tokens de elevación 1–24) se construye encima del
Nivel 2. RichFaces ya soporta **herencia de skins** (`baseSkin`) y
**referencias entre propiedades** (prefijo `&`), vistas en `SkinImpl`. Eso da la
base para un sistema de tokens: un skin base "material-core" con la lógica y las
funciones, y skins hijos que solo definen la paleta.

### 5.5 Lo que NO se puede hacer solo con skinning

- **Ripple effect** (onda al hacer clic): es comportamiento, necesita
  JavaScript. El skinning solo produce CSS. Se puede añadir con JS propio, pero
  no viene del `.skin.properties`.
- **Animaciones `@keyframes` complejas:** el parser podría aceptar la sintaxis,
  pero orquestarlas por estado de componente a veces requiere tocar el JS del
  componente.
- **Cambiar la estructura HTML** que genera cada componente: eso ya no es
  skinning, es tocar los renderers / CDK.

---

## 6. Resumen para decidir

Sí existe la forma de que RichFaces lea y genere skins modernas de forma nativa,
y el esfuerzo es menor de lo esperado porque el motor ya es un sistema de
plantillas con variables EL:

- **Colores, tipografía, radios y sombras estáticas Material:** sin programar
  Java, solo añadir propiedades y editar `.ecss` (Nivel 1).
- **Colores derivados automáticos (theming inteligente):** programar funciones
  EL de color en el `core` (Nivel 2, la vía más rentable).
- **Ripple y micro-animaciones por estado:** requieren JavaScript aparte, fuera
  del skinning.

El punto exacto donde intervendría el código Java (Nivel 2) es el paquete
`org.richfaces.skin` del `core` (añadir las funciones) y los `.ecss` de
`components/rich` y `core` (consumirlas). El parser y el pipeline de servido no
se tocan.

### Recomendación de arranque

Empezar por el **Nivel 1** sobre el mini-proyecto Liberty: crear un
`material.skin.properties` y ampliar un par de `.ecss` (panel y botón) para
verlo en el navegador, y decidir después si merece la pena subir al Nivel 2.

Como alternativa rápida sin tocar la librería, el **Enfoque B (CSS override)**
sigue siendo lo más veloz para un resultado visible.
