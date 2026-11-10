import { execFileSync } from 'node:child_process';

const tracked = execFileSync('git', ['ls-files', '-z'], { encoding: 'utf8' }).split('\0').filter(Boolean);
const forbidden = tracked.filter((file) =>
  /(^|\/)(\.env($|\.(?!example$))|\.local(?:-[^/]+)?(\/|$)|\.cloud(\/|$)|\.vercel(\/|$))|\.(dump|bak|sqlite3?|db|pem|key|p12|pfx)$/i.test(file),
);
if (forbidden.length) {
  console.error('Arquivos privados versionados; remova-os do índice antes de publicar:\n' + forbidden.join('\n'));
  process.exitCode = 1;
} else {
  console.log('Nenhum arquivo privado ou backup versionado.');
}

