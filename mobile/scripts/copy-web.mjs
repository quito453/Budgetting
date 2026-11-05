// Copies the built web app (the repo root, which is what Netlify serves) into www/ for the native apps.
import { cpSync, rmSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..', '..');
const www = join(here, '..', 'www');
rmSync(www, { recursive: true, force: true });
mkdirSync(www, { recursive: true });
// sw.js stays out: inside the app the files are already local, so there is nothing to cache.
for (const f of ['index.html', 'config.js', 'manifest.webmanifest', 'privacidad.html', 'vendor', 'icons', 'mascot']) {
  const src = join(root, f);
  if (!existsSync(src)) { console.error('Falta ' + f + ' en la carpeta principal'); process.exit(1); }
  cpSync(src, join(www, f), { recursive: true });
}
console.log('Listo: la app web se copió a mobile/www');
