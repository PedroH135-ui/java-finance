import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import type { Plugin } from 'vite';
export function pwaBuild(): Plugin {
  return {
    name: 'java-finance-pwa', apply: 'build',
    generateBundle(_options, bundle) {
      const assets = ['/offline.html', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png', '/icons/icon-maskable-512.png', '/icons/apple-touch-icon.png', ...Object.keys(bundle).filter(file => file.startsWith('assets/')).map(file => '/' + file)];
      const template = readFileSync(new URL('./worker.js', import.meta.url), 'utf8');
      const hash = createHash('sha256').update(JSON.stringify(assets)).update(template);
      for (const asset of assets.filter(path => !path.startsWith('/assets/'))) hash.update(readFileSync(new URL('../public' + asset, import.meta.url)));
      const version = hash.digest('hex').slice(0, 16);
      const worker = template.replace('__CACHE_VERSION__', 'java-finance-shell-' + version).replace('__PUBLIC_ASSETS__', JSON.stringify(assets));
      this.emitFile({ type: 'asset', fileName: 'sw.js', source: worker });
    },
  };
}
