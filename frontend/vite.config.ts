import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { pwaBuild } from './pwa/build';
export default defineConfig({
  plugins: [react(), pwaBuild()],
  server: { port: 5173, strictPort: true, proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } } },
  build: { sourcemap: false },
});

