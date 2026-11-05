# Atajo de Back Tap listo para tus usuarios (versión web)

Lo armas **una sola vez** en tu iPhone. Después lo compartes como enlace de iCloud y Valiu se lo ofrece a cada usuario: un toque para instalarlo y **Pegar** su enlace, que Valiu ya deja copiado.

> En la app de App Store esto no hace falta: Valiu trae sus propias acciones ("Anotar gasto en Valiu" y "Registrar compra en Valiu").

## 1. Crear el atajo "Anotar en Valiu"

En la app **Atajos**, toca **+** y nómbralo **Anotar en Valiu**. Luego agrega estas acciones en orden:

1. **Texto**: escribe `PEGA-TU-ENLACE`.
   Esta acción guarda el enlace de cada usuario. El paso 2 hace que se lo pregunte al instalarlo.
2. **Solicitar entrada** (Ask for Input):
   - Tipo: **Número**
   - Pregunta: `¿Cuánto gastaste?`
3. **Solicitar entrada**:
   - Tipo: **Texto**
   - Pregunta: `¿En qué?`
4. **Obtener contenido de URL**:
   - URL: toca el campo y elige la variable **Texto** (la acción 1).
   - Toca la flecha **›**. Método: **POST**. Cuerpo de la solicitud: **JSON**. Agrega tres campos de texto:
     - `amount` → variable **Entrada proporcionada** de la acción 2
     - `merchant` → variable **Entrada proporcionada** de la acción 3
     - `card` → escribe `Back Tap`
5. **Mostrar notificación**: `Anotado en Valiu ✓` (opcional).

## 2. Que pida el enlace al instalarlo

1. Toca el nombre del atajo arriba → **ⓘ Detalles** → **Configurar** → **Preguntas de importación** → **Agregar pregunta**.
2. Elige el campo `PEGA-TU-ENLACE` de la acción Texto.
3. Pregunta: `Pega tu enlace de Valiu (ya está copiado)`.

## 3. Compartirlo

1. Toca **Compartir** → **Copiar enlace de iCloud**.
2. Abre `config.js` (en GitHub) y pégalo en `tapShortcutUrl: '…'`. También me lo puedes mandar y lo pongo yo.

Listo. En Perfil → Back Tap, cada usuario verá **Instalar el atajo de Back Tap**. Valiu copia su enlace, abre el atajo, el usuario toca **Pegar** → **Agregar atajo**, y solo le falta:

**Ajustes → Accesibilidad → Tocar → Tocar atrás → Doble toque → Anotar en Valiu**

## Antes de compartir, pruébalo

Ejecútalo con tu propio enlace. Debe responder `{"ok":true}` y el gasto aparece en Valiu.

Si dice **"Requested function was not found"**, falta publicar la función **gasto** en Supabase (Edge Functions), con **Verify JWT apagado**.
