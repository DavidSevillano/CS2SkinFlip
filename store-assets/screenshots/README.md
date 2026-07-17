# Play Store screenshots

Genera los 42 PNG de la ficha: 6 screenshots (1080×1920) + 1 feature graphic
(1024×500), × 6 locales.

Los screenshots se capturan a 2x (viewport 540×960) porque Play acepta de 320 a
3840 px de lado. El feature graphic va a 1x: Play lo exige en **1024×500 exactos**
y rechaza cualquier otro tamano, asi que 2x no vale. `capture.mjs` verifica las
dimensiones de cada PNG contra la cabecera IHDR y peta si no cuadran.

## Uso

    npm install     # descarga Chromium la primera vez (~1.3GB en ~/.cache/puppeteer)
    npm run all     # build + capture
    npm test        # tests del lector de strings y del catalogo

Salida en `out/{play-locale}/`. Subir a Play Console → Ficha principal, por idioma.
`es-419` reusa los PNG de `es-ES`.

## De donde sale el texto

Dos fuentes:

- **`src/strings.mjs`** lee `android/app/src/main/res/values-*/strings.xml`.
  Toda la UI simulada dentro del telefono sale de aqui, asi que los screenshots
  reflejan la app real. Si cambias una traduccion en la app, el screenshot la
  hereda. Falta una clave en un locale → cae al ingles. No existe en ningun
  sitio → la build peta.
- **`src/copy.mjs`** tiene el copy de marketing (captions, feature graphic), que
  no existe en la app. Al anadir una clave hay que anadirla en los 6 idiomas: hay
  un test que lo comprueba.

Los nombres de skins (`AK-47 | Redline`) no se traducen: son market hash names de
Steam, identicos en todos los idiomas.

## Textos largos

Ruso, polaco y turco son un 20-40% mas largos que el ingles. `.caption` tiene
altura fija (190px) para que el telefono mida lo mismo en los 6 idiomas — con
altura automatica, un caption alto encogeria `.phone` (que es `flex:1`) y tendrias
6 fichas con el movil a distinta escala. Un script de auto-fit inline en el HTML
encoge `.headline` / `.eyebrow` por busqueda binaria hasta que quepan.
`capture.mjs` falla ruidosamente si algo desborda igualmente.

El script va en el HTML generado, no en `capture.mjs`: asi cada fichero es
self-contained y puedes abrirlo en el navegador y ver exactamente lo que se va a
capturar.

## Limitaciones conocidas

- La captura depende de la red: las imagenes de skins vienen de Steam.
- Los precios se quedan en formato US (`$1,942.00`), igual que los sirve el
  backend. Localizar el separador decimal implicaria decidir que hace la app real.
- El render depende de `Segoe UI`. En Linux no existe y las metricas cambian.
- El copy de `copy.mjs` no esta revisado por hablantes nativos.
