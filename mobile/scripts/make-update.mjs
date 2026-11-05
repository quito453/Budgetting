// Runs on Netlify with every deploy (see netlify.toml). It packs the same files the store apps carry into
// updates/valiu-<version>.zip and writes updates/app.json, which the apps check to update themselves.
// No packages needed: the zip is written with Node's own zlib.
import { readFileSync, writeFileSync, mkdirSync, readdirSync, statSync, existsSync, rmSync } from 'node:fs';
import { join, dirname, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateRawSync } from 'node:zlib';
import { createHash } from 'node:crypto';
import { WEB_FILES } from './web-files.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..', '..');
const SITE = 'https://valiugt.netlify.app';

const html = readFileSync(join(root, 'index.html'), 'utf8');
const m = html.match(/window\.VALIU_BUILD = '(\d{4}\.\d{2}\.\d{2}-\d{4})'/);
if (!m) { console.error('No encontré VALIU_BUILD en index.html'); process.exit(1); }
const version = m[1];
const settings = JSON.parse(readFileSync(join(here, '..', 'live-update.json'), 'utf8'));

// ---- tiny zip writer ----
const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = buf => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const files = [];
const walk = p => statSync(p).isDirectory() ? readdirSync(p).sort().forEach(f => walk(join(p, f))) : files.push(p);
for (const f of WEB_FILES) {
  const p = join(root, f);
  if (!existsSync(p)) { console.error('Falta ' + f); process.exit(1); }
  walk(p);
}
const local = [], central = [];
let offset = 0;
for (const p of files) {
  const name = Buffer.from(relative(root, p).split(sep).join('/'));
  const data = readFileSync(p);
  const packed = deflateRawSync(data, { level: 9 });
  const crc = crc32(data);
  const h = Buffer.alloc(30);
  h.writeUInt32LE(0x04034b50, 0); h.writeUInt16LE(20, 4); h.writeUInt16LE(0x0800, 6); h.writeUInt16LE(8, 8);
  h.writeUInt16LE(0, 10); h.writeUInt16LE(0x21, 12);   // fixed date: same files give the same zip
  h.writeUInt32LE(crc, 14); h.writeUInt32LE(packed.length, 18); h.writeUInt32LE(data.length, 22);
  h.writeUInt16LE(name.length, 26); h.writeUInt16LE(0, 28);
  local.push(h, name, packed);
  const c = Buffer.alloc(46);
  c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(0x0800, 8); c.writeUInt16LE(8, 10);
  c.writeUInt16LE(0, 12); c.writeUInt16LE(0x21, 14);
  c.writeUInt32LE(crc, 16); c.writeUInt32LE(packed.length, 20); c.writeUInt32LE(data.length, 24);
  c.writeUInt16LE(name.length, 28); c.writeUInt32LE(offset, 42);
  central.push(c, name);
  offset += 30 + name.length + packed.length;
}
const cd = Buffer.concat(central);
const end = Buffer.alloc(22);
end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(files.length, 8); end.writeUInt16LE(files.length, 10);
end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(offset, 16);
const zip = Buffer.concat([...local, cd, end]);

const out = join(root, 'updates');
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
const zipName = 'valiu-' + version + '.zip';
writeFileSync(join(out, zipName), zip);
const manifest = {
  version,
  url: SITE + '/updates/' + zipName,
  checksum: createHash('sha256').update(zip).digest('hex'),
  minNativeBuild: settings.minNativeBuild || 1,
};
writeFileSync(join(out, 'app.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log('Actualización en vivo lista:', version, '·', files.length, 'archivos ·', Math.round(zip.length / 1024), 'KB');
