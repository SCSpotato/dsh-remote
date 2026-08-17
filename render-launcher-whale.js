const fs = require('fs');
const path = require('path');
const sharp = require('C:/Users/SCS/dsh-app/node_modules/sharp');

const RES = 'C:/Users/SCS/Desktop/dsh/8.16/DshRemote/app/src/main/res';
const FG = path.join(RES, 'drawable', 'ic_launcher_foreground.xml');

// Extract the whale path from the existing 1.1.0 foreground vector.
const fg = fs.readFileSync(FG, 'utf8');
const m = fg.match(/android:pathData="([^"]+)"/);
if (!m) { console.error('whale path not found'); process.exit(1); }
const whaleD = m[1];

// Blue tile + centered white whale + a concentric wifi signal (3 arcs + center dot) at top-right.
const svg = `<svg width="108" height="108" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">
<rect width="108" height="108" fill="#4D6BFE"/>
<g transform="translate(21 21) scale(1.32)">
<path d="${whaleD}" fill="#FFFFFF"/>
</g>
<g stroke="#FFFFFF" stroke-linecap="round" fill="none" stroke-width="3.5">
<path d="M78 22 A 6 6 0 0 1 84 28"/>
<path d="M78 16 A 12 12 0 0 1 90 28"/>
<path d="M78 10 A 18 18 0 0 1 96 28"/>
</g>
<circle cx="78" cy="28" r="2.6" fill="#FFFFFF"/>
</svg>`;

const svgPath = path.join(__dirname, 'launcher-whale-wifi.svg');
fs.writeFileSync(svgPath, svg);

const densities = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };

(async () => {
  const base = sharp(svgPath, { density: 384 });
  for (const [d, size] of Object.entries(densities)) {
    const dir = path.join(RES, 'mipmap-' + d);
    fs.mkdirSync(dir, { recursive: true });
    for (const name of ['ic_launcher.png', 'ic_launcher_round.png']) {
      await base.clone().resize(size, size).png().toFile(path.join(dir, name));
    }
  }
  console.log('done');
})().catch(e => { console.error(e); process.exit(1); });
