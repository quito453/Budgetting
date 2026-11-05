# Valiu en App Store y Google Play

La app de las tiendas es la misma app web, empaquetada con Capacitor. Cada vez que cambias la app web, actualizas las tiendas con:

```
cd mobile
npm run sync
```

Identificador de la app (no se puede cambiar después de publicar): **com.valiugt.app**

---

## 0. Antes de empezar (una sola vez)

1. **Llena el aviso de privacidad.** En `privacidad.html` cambia `[Nombre completo o razón social del responsable]`, `[ciudad]` y `[correo de contacto]`. Las dos tiendas piden ese enlace: `https://valiugt.netlify.app/privacidad.html`.
2. **Supabase → SQL Editor:** corre `supabase/update-5-app-nativa.sql`. Crea la tabla donde cada teléfono guarda su dirección para recibir recordatorios.
3. **Supabase → Edge Functions → recordatorios:** vuelve a publicarla con el código nuevo de `supabase/functions/recordatorios/index.ts`. Ahora también envía a la app de iPhone y Android.

---

## 1. Cuentas de desarrollador

| | Dónde | Costo |
|---|---|---|
| Apple | https://developer.apple.com/programs/enroll/ | USD 99 al año |
| Google | https://play.google.com/console/signup | USD 25 una vez |

- Usa tu Apple ID normal. La aprobación tarda de uno a varios días.
- En Google elige cuenta **personal**. Te pedirán tu DPI o pasaporte.

---

## 2. Firebase (notificaciones en Android)

1. Entra a https://console.firebase.google.com y crea un proyecto **Valiu**. Puedes desactivar Analytics.
2. **Agregar app → Android**, con nombre de paquete `com.valiugt.app`. Descarga `google-services.json` y ponlo en `mobile/android/app/` (no se sube a GitHub).
3. **Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada.** Se descarga un archivo `.json`.
4. En Supabase → Edge Functions → **Secrets**, crea `FCM_SERVICE_ACCOUNT` y pega **todo** el contenido de ese `.json`.

> Ese archivo de cuenta de servicio es secreto: no lo subas a GitHub ni lo compartas.

---

## 3. Notificaciones en iPhone (APNs)

1. En https://developer.apple.com/account → **Certificates, IDs & Profiles → Keys → +**.
2. Nombre: `Valiu push`. Marca **Apple Push Notifications service (APNs)** y descarga el archivo `AuthKey_XXXXXXXXXX.p8`. Solo se descarga una vez; guárdalo bien.
3. En Supabase → Edge Functions → **Secrets**, crea:
   - `APNS_KEY_P8`: pega el contenido completo del `.p8`, desde `-----BEGIN PRIVATE KEY-----` hasta el final.
   - `APNS_KEY_ID`: los 10 caracteres del nombre del archivo (`XXXXXXXXXX`).
   - `APNS_TEAM_ID`: tu Team ID, que aparece arriba a la derecha en developer.apple.com.
   - `APNS_BUNDLE_ID`: `com.valiugt.app`
   - `APNS_ENV`: `sandbox` mientras pruebas desde Xcode; cámbialo a `production` cuando la app esté en TestFlight o en la tienda.

---

## 4. iPhone: compilar en tu MacBook

**Instalar (una vez):**
- **Xcode**, desde la App Store de la Mac. Ábrelo una vez y acepta todo.
- **Node.js LTS**, desde https://nodejs.org.
- **El proyecto:**
  ```
  git clone https://github.com/quito453/Budgetting.git
  cd Budgetting/mobile
  npm install
  npm run ios
  ```
  Eso abre el proyecto en Xcode.

**En Xcode:**
1. A la izquierda, toca **App** (el ícono azul) → target **App** → pestaña **Signing & Capabilities**.
2. Marca **Automatically manage signing** y en **Team** elige tu cuenta de Apple Developer.
3. Toca **+ Capability** y agrega **Push Notifications**.
4. Conecta tu iPhone con cable, elígelo arriba y toca ▶︎. La primera vez, en el iPhone ve a Ajustes → General → VPN y gestión de dispositivos y confía en tu cuenta.
5. Prueba: crea cuenta, agrega una tarjeta con estado de cuenta y activa los recordatorios en Perfil.

**Subir a la App Store:**
1. Arriba elige **Any iOS Device (arm64)** → menú **Product → Archive**.
2. Cuando termine: **Distribute App → App Store Connect → Upload**.
3. En https://appstoreconnect.apple.com → **Apps → +** → nueva app:
   - Nombre "Valiu" (si está ocupado, "Valiu – Presupuesto").
   - Idioma principal: Español (México).
   - Bundle ID: `com.valiugt.app`
   - SKU: `valiu-001`
4. En **TestFlight** aparecerá la versión que subiste. Úsala para probar tú y tus 15 usuarios, y después pon `APNS_ENV` en `production`.
5. Llena la ficha (sección 6) y toca **Enviar para revisión**.

---

## 5. Android: compilar

Puedes hacerlo en la misma Mac.

1. Instala **Android Studio**: https://developer.android.com/studio
2. En la carpeta `mobile`, corre `npm run android`. Se abre Android Studio; espera a que termine "Gradle sync".
3. Prueba en un Android conectado por cable (con Depuración USB activada) o en un emulador: botón ▶︎.
4. Para la tienda: **Build → Generate Signed App Bundle / APK → Android App Bundle** → **Create new** keystore.
   - Guarda el archivo `.jks` y sus contraseñas en un lugar seguro. **Si lo pierdes, no podrás actualizar la app nunca.** No lo subas a GitHub.
   - Elige **release**. El resultado es un archivo `.aab`.
5. En https://play.google.com/console → **Crear app** → nombre Valiu, español, app, gratis.
6. **Prueba cerrada:**
   - Ve a Pruebas → Prueba cerrada → crea una versión → sube el `.aab`.
   - En **Testers**, crea una lista con los correos Gmail de al menos **12 personas**. Tus usuarios actuales sirven.
   - Cada una debe aceptar la invitación desde el enlace que te da Play Console e instalar la app.
   - Necesitas **12 personas inscritas durante 14 días seguidos**. Después aparece el botón **Solicitar acceso a producción**.
7. Llena la ficha (sección 6) y publica en **Producción**.

---

## 6. Ficha de la tienda (sirve para las dos)

**Nombre:** Valiu

**Subtítulo / descripción corta:**
Tu presupuesto diario, tus deudas y tus metas, con una ranita que te acompaña.

**Descripción:**
> Valiu te dice cuánto puedes gastar hoy sin descuadrar tus pagos ni tu ahorro.
>
> • Un número claro cada día, calculado con tu ingreso, tus pagos fijos y tus deudas.
> • Recordatorios antes de que venzan tus tarjetas.
> • Un plan para salir de deudas más rápido: avalancha o bola de nieve, con fecha estimada y cuánto ahorras en intereses.
> • Compara tasas reales de tarjetas y préstamos en Guatemala.
> • Fondo de emergencia y metas de ahorro con aportes rápidos.
> • Anota gastos sin escribir: con Apple Pay o con dos toques en la parte de atrás de tu iPhone.
> • Sueldo fijo, ingreso flexible o remesas, con cálculo de IGSS e ISR si lo deseas.
>
> Tus datos son privados, se protegen con PIN y puedes borrar tu cuenta cuando quieras.

- **Categoría:** Finanzas
- **Clasificación por edad:** 4+ en Apple. En Google, responde el cuestionario: sin violencia, sin apuestas, etc.
- **Correo de soporte:** el de tu aviso de privacidad.
- **URL de soporte / sitio:** https://valiugt.netlify.app
- **Política de privacidad:** https://valiugt.netlify.app/privacidad.html

**Capturas de pantalla** (te las preparo yo):
- iPhone de 6.9" (1320 × 2868)
- Android teléfono (1080 × 1920 o similar)
- Imagen destacada de Google Play (1024 × 500)

**Cuenta de prueba para los revisores.** Apple y Google la piden. Crea una cuenta con datos de ejemplo, por ejemplo `revision@valiu…`, con su contraseña y PIN, y escríbelos en las notas para el revisor.

---

## 7. Formularios de privacidad

**Apple → App Privacy** ("Datos vinculados a ti"; no se usan para rastrear):
- **Información de contacto:** correo electrónico (funcionalidad de la app, cuenta).
- **Información financiera:** otra información financiera, porque el usuario escribe sus ingresos, gastos y deudas (funcionalidad de la app).
- **Identificadores:** ID de usuario (funcionalidad de la app).
- **Tracking:** No.

**Google Play → Seguridad de los datos:**
- ¿Recopila datos? Sí. ¿Comparte con terceros? No.
- **Datos personales:** dirección de correo (cuenta).
- **Información financiera:** otra información financiera (funcionalidad de la app).
- **ID de la app/dispositivo:** sí, para notificaciones.
- Datos cifrados en tránsito: Sí. El usuario puede pedir que se borren: Sí (desde la app, Perfil → Eliminar mi cuenta).

**Google Play → Funciones financieras:** marca solo "Administración de finanzas personales / presupuesto". **No** marques préstamos, inversiones ni pagos: Valiu no presta, no invierte y no mueve dinero.

---

## 8. Cuando cambies la app

**Cambios normales (pantallas, textos, cálculos, colores):** no tienes que hacer nada en las tiendas.
1. Haz el cambio en la web, como siempre (GitHub → Netlify).
2. Netlify publica también una copia para las apps (`updates/app.json`).
3. Cada app la descarga sola en segundo plano y la usa la siguiente vez que el usuario sale y vuelve a entrar. Si la copia nueva fallara al abrir, la app regresa sola a la anterior.

**Cambios nativos (un plugin nuevo como Face ID, permisos, íconos, nombre de la app):** sí hay que pasar por las tiendas.
1. Sube el número de versión:
   - **iPhone:** en Xcode → General → Version/Build.
   - **Android:** en `android/app/build.gradle`, sube `versionCode` y `versionName`.
2. En la Mac, dentro de `Budgetting/mobile`: `git pull` y luego `npm run sync`.
3. Vuelve a archivar (iPhone) y a generar el `.aab` (Android) y súbelos.
4. En `mobile/live-update.json`, pon `minNativeBuild` en ese nuevo número de build. Así los teléfonos con la app vieja no reciben código que necesita la parte nativa nueva hasta que actualicen desde la tienda.
