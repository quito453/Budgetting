# Valiu · Guía para publicar la versión con login

Esta carpeta es la app completa, con inicio de sesión (correo y contraseña) y PIN de 6 dígitos. Los datos de cada usuario se guardan en **Supabase**, y la app se publica gratis en **Netlify**. Tiempo estimado: 20 minutos.

---

## 1. Crea tu proyecto en Supabase

1. Entra a **supabase.com** y crea una cuenta (puedes usar GitHub o tu correo).
2. Toca **New project**:
   - **Name:** `valiu`
   - **Database password:** genera una y guárdala en un lugar seguro (la app no la usa, pero Supabase la pide).
   - **Region:** la más cercana a Guatemala, por ejemplo *East US*.
3. Espera 1 o 2 minutos a que el proyecto termine de crearse.

## 2. Crea la tabla de datos

1. En el menú izquierdo abre **SQL Editor** → **New query**.
2. Abre el archivo `supabase/schema.sql` de esta carpeta, copia todo y pégalo.
3. Toca **Run**. Debe decir *Success*.

Esto crea una fila privada por usuario. Las reglas de seguridad (RLS) hacen que cada persona solo pueda leer y cambiar sus propios datos.

## 3. Conecta la app con tu proyecto

1. En Supabase toca el botón **Connect**, arriba en el panel de tu proyecto. Ahí aparecen la **Project URL** y la **publishable key**. También las encuentras en **Settings** → **API Keys**.
2. Copia:
   - **Project URL** (se ve como `https://abcdxyz.supabase.co`)
   - La **publishable key** (empieza con `sb_publishable_`). Si solo ves la clave **anon** (empieza con `eyJ`), también funciona.
3. Abre `config.js` con cualquier editor de texto y reemplaza:
   ```js
   supabaseUrl: 'https://abcdxyz.supabase.co',
   supabaseAnonKey: 'tu-clave-publica'
   ```
4. Guarda el archivo.

> ⚠️ **Nunca pegues la clave `secret` (empieza con `sb_secret_`) ni la `service_role`.** Esas claves saltan todas las reglas de seguridad.

## 4. Publica la app en Netlify

1. Entra a **app.netlify.com/drop**.
2. Arrastra **esta carpeta completa** a la página.
3. Netlify te da una dirección como `https://nombre-al-azar.netlify.app`. Crea una cuenta gratuita para que el sitio no se borre y para ponerle un nombre, por ejemplo `valiu-gt.netlify.app`.

Cada vez que quieras actualizar la app, arrastra la carpeta otra vez en **Deploys** de tu sitio.

## 5. Dile a Supabase cuál es tu dirección

Esto hace que los enlaces de confirmación y de recuperación de contraseña regresen a tu app.

1. En Supabase abre **Authentication** → **URL Configuration**.
2. **Site URL:** tu dirección de Netlify, por ejemplo `https://valiu-gt.netlify.app`.
3. En **Redirect URLs** agrega la misma dirección.
4. Guarda.

## 6. Prueba

1. Abre tu dirección de Netlify → **Crear cuenta**.
2. Confirma el correo con el enlace que te llega. Ábrelo desde el mismo dispositivo.
3. Crea tu PIN de 6 dígitos.
4. Cierra y vuelve a abrir la app: ahora entras solo con tu correo y tu PIN.
5. En otro dispositivo, la primera vez entras con tu contraseña, creas un PIN para ese dispositivo y ves los mismos datos.

## 7. Pasa tus datos desde la versión de Claude

1. En la versión de Claude: **Perfil** → **Copiar mis datos**.
2. En la nueva versión: **Perfil** → **Pegar datos** → **Reemplazar con estos datos**.

## 8. Instálala en el celular

- **Android (Chrome):** menú ⋮ → **Instalar app**.
- **iPhone (Safari):** botón Compartir → **Agregar a inicio**.

Se abre a pantalla completa, con su propio ícono, y funciona aunque no tengas internet. Los cambios se sincronizan cuando vuelve la conexión.

---

## Cómo funciona la seguridad

- **Contraseña:** la guarda Supabase cifrada. Ni tú ni la app pueden verla.
- **PIN:** nunca sale del dispositivo. Con él se cifra (AES-256) la sesión guardada en ese celular o compu. Por eso el PIN solo funciona en dispositivos donde ya entraste con tu contraseña.
- **5 PIN incorrectos** borran la sesión del dispositivo y piden la contraseña otra vez.
- **Bloqueo automático:** si la app queda en segundo plano más de 2 minutos, vuelve a pedir el PIN.
- **¿Olvidaste el PIN?** Toca "Usar mi contraseña", entra y crea uno nuevo.
- **Cerrar sesión** borra la sesión y los datos guardados en ese dispositivo. Tus datos siguen en tu cuenta.

## Antes de abrirla a más personas

- **Correos:** Supabase envía pocos correos por hora con su servicio incluido, que es para pruebas. Para muchos usuarios configura tu propio servicio de correo en **Authentication** → **Emails** → **SMTP Settings** (por ejemplo Resend o Brevo, que tienen planes gratis).
- **Plan gratis:** Supabase puede pausar un proyecto gratuito que no se usa en una semana. Se reactiva desde el panel. Con usuarios reales activos no pasa, o puedes pasar al plan Pro.
- **Aviso de privacidad:** como vas a guardar datos financieros de otras personas, publica un aviso de privacidad sencillo y considera revisarlo con un abogado.
- **Borrar cuentas:** por ahora, si alguien pide borrar su cuenta, hazlo en Supabase → **Authentication** → **Users** → ⋯ → **Delete user**. Sus datos se borran automáticamente.

---

## Una sola fuente para todo: GitHub

Tu repositorio **quito453/Budgetting** es el lugar donde vive la app. GitHub Pages y Netlify publican lo que haya ahí, así que un cambio en GitHub se ve en los dos sitios. Los datos de los usuarios están en Supabase y no dependen de dónde abras la app.

### A. Sube esta versión a tu repositorio
1. Antes de reemplazar nada, abre tu página actual `https://quito453.github.io/Budgetting/`. No hace falta copiar tus datos: la nueva versión los importa sola la primera vez que entres con tu cuenta **desde esa misma dirección**.
2. Copia **tu** `config.js` (el que ya tiene tu URL y tu clave pública) dentro de esta carpeta, reemplazando el que viene vacío.
3. En GitHub abre tu repositorio → **Add file** → **Upload files**.
4. Arrastra **el contenido** de esta carpeta (no la carpeta misma): `index.html`, `config.js`, `sw.js`, `manifest.webmanifest` y las carpetas `icons`, `vendor` y `supabase`.
5. Escribe un mensaje, por ejemplo "Versión con login", y toca **Commit changes**.
6. En 1 o 2 minutos GitHub Pages muestra la versión nueva. Tu versión anterior queda guardada en el historial del repositorio por si la necesitas.

La clave pública (`sb_publishable_...`) puede estar en GitHub sin problema. **Nunca subas la clave secreta.**

### B. Conecta Netlify al mismo repositorio
1. En Netlify: **Add new site** → **Import an existing project** → **GitHub** → elige **Budgetting**.
2. Deja vacíos el comando de build y el directorio de publicación (o pon `/`) y toca **Deploy**.
3. Desde ahora, cada vez que cambies algo en GitHub, Netlify se actualiza solo.
4. Si prefieres no tener dos direcciones, puedes borrar el sitio anterior que subiste arrastrando la carpeta (**Site configuration** → **Delete site**).

### C. Agrega las dos direcciones en Supabase
**Authentication** → **URL Configuration** → **Redirect URLs** → agrega cada dirección que uses:
- `https://quito453.github.io/Budgetting/`
- `https://<tu-sitio>.netlify.app`

En **Site URL** deja la dirección principal que vas a compartir con tus usuarios.

### D. Cómo hacer cambios de aquí en adelante
- **Pídemelos en el chat:** te entrego los archivos actualizados y los subes con **Add file** → **Upload files**. GitHub Pages y Netlify se actualizan solos.
- **Más rápido:** conecta tu cuenta de GitHub a Claude (en claude.ai: **Settings** → **Connectors** → **GitHub**). Así puedo subir los cambios yo directamente a tu repositorio.
