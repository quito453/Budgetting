// Puts Valiu's iPhone widgets into the Xcode project.
// First create the widget target once in Xcode (see GUIA.md → "Widgets de iPhone"), then run:
//   npm run ios-widget
// It replaces the sample files Xcode made with Valiu's widgets. Safe to run again after updates.
import { cpSync, existsSync, readdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = join(root, 'ios-widget', 'ValiuWidget');
const dest = join(root, 'ios', 'App', 'ValiuWidget');

if (!existsSync(dest)) {
    console.error('No encontré la carpeta ios/App/ValiuWidget.');
    console.error('Primero crea el widget en Xcode: File → New → Target → Widget Extension, nombre "ValiuWidget" (ver GUIA.md).');
    process.exit(1);
}
// Xcode's sample files: Valiu brings its own.
for (const f of ['ValiuWidget.swift', 'ValiuWidgetControl.swift', 'ValiuWidgetLiveActivity.swift', 'AppIntent.swift', 'Assets.xcassets']) {
    const p = join(dest, f);
    if (existsSync(p)) rmSync(p, { recursive: true, force: true });
}
for (const f of readdirSync(src)) cpSync(join(src, f), join(dest, f), { recursive: true });
console.log('Listo: los widgets de Valiu están en ios/App/ValiuWidget. Vuelve a Xcode y dale ▶︎.');
