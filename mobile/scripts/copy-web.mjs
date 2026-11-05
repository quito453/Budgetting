// Copies the built web app (the repo root, which is what Netlify serves) into www/ for the native apps.
import { cpSync, rmSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { WEB_FILES } from './web-files.mjs';
const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..', '..');
const www = join(here, '..', 'www');
rmSync(www, { recursive: true, force: true });
mkdirSync(www, { recursive: true });
for (const f of WEB_FILES) {
  const src = join(root, f);
  if (!existsSync(src)) { console.error('Falta ' + f + ' en la carpeta principal'); process.exit(1); }
  cpSync(src, join(www, f), { recursive: true });
}
console.log('Listo: la app web se copió a mobile/www');
