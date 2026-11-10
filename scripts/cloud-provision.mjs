import { spawnSync } from 'node:child_process';
import { mkdirSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

// Run only against the dedicated, already-created Java Finance projects.
const [neonCli, vercelCli] = process.argv.slice(2);
if (!neonCli || !vercelCli) throw new Error('Provide paths to neonctl and Vercel CLI JavaScript entrypoints.');
const root = resolve(import.meta.dirname, '..');
const project = process.env.NEON_PROJECT_ID?.trim();
const scope = process.env.VERCEL_SCOPE?.trim();
if (!project || !scope || !/^[a-zA-Z0-9][a-zA-Z0-9_-]*$/.test(project) || !/^[a-zA-Z0-9][a-zA-Z0-9_-]*$/.test(scope)) {
  throw new Error('Set NEON_PROJECT_ID and VERCEL_SCOPE privately before provisioning.');
}
const container = 'java-finance-db-1';
let password = '';
function run(command, args, options = {}) {
  const result = spawnSync(command, args, { cwd: root, encoding: 'utf8', windowsHide: true, ...options });
  if (result.status !== 0) {
    const safe = `${result.stderr ?? ''}`.replaceAll(password || '\0', '[redacted]').replace(/postgres(?:ql)?:\/\/\S+/g, '[connection redacted]');
    throw new Error(`${command} failed: ${safe.slice(-1800)}`);
  }
  return result.stdout.trim();
}
const connection = run(process.execPath, [neonCli, 'connection-string', '--project-id', project, '--database-name', 'java_finance', '--role-name', 'java_finance_owner', '--ssl', 'verify-full']);
const uri = new URL(connection.replace(/^"|"$/g, ''));
password = decodeURIComponent(uri.password);
const user = decodeURIComponent(uri.username);
const database = uri.pathname.slice(1);
if (database !== 'java_finance' || !uri.hostname.endsWith('.neon.tech')) throw new Error('Unexpected destination.');
const pgEnv = { ...process.env, PGPASSWORD: password, PGSSLMODE: 'verify-full', PGSSLROOTCERT: '/etc/ssl/cert.pem' };
const pgArgs = ['-h', uri.hostname, '-U', user, '-d', database];
function remoteSql(sql) {
  return run('docker', ['exec', '-e', 'PGPASSWORD', '-e', 'PGSSLMODE', '-e', 'PGSSLROOTCERT', container, 'psql', ...pgArgs, '-v', 'ON_ERROR_STOP=1', '-Atc', sql], { env: pgEnv });
}
const tables = Number(remoteSql("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'"));
if (tables === 0) {
  mkdirSync(resolve(root, '.cloud'), { recursive: true });
  const backup = resolve(root, '.cloud/local-before-cloud.dump');
  if (existsSync(backup)) throw new Error('A backup already exists; review it before retrying an empty destination.');
  run('docker', ['exec', container, 'pg_dump', '-U', 'java_finance', '-d', 'java_finance', '-Fc', '--no-owner', '--no-privileges', '-f', '/tmp/java-finance-cloud-20261007.dump']);
  run('docker', ['cp', `${container}:/tmp/java-finance-cloud-20261007.dump`, backup]);
  run('docker', ['exec', '-e', 'PGPASSWORD', '-e', 'PGSSLMODE', '-e', 'PGSSLROOTCERT', container, 'pg_restore', ...pgArgs, '--no-owner', '--no-privileges', '--exit-on-error', '/tmp/java-finance-cloud-20261007.dump'], { env: pgEnv });
  console.log('Backup preserved; local database copied to the dedicated Neon database over verified TLS.');
}
const countsSql = "SELECT (SELECT count(*) FROM app_user), (SELECT count(*) FROM movement)";
const localCounts = run('docker', ['exec', container, 'psql', '-U', 'java_finance', '-d', 'java_finance', '-Atc', countsSql]);
const cloudCounts = remoteSql(countsSql);
if (localCounts !== cloudCounts) throw new Error('Local/cloud record counts differ; deployment configuration stopped.');
console.log(`Verified local/cloud users and movements: ${cloudCounts}`);
const variables = {
  DB_URL: `jdbc:postgresql://${uri.hostname}:5432/${database}?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`,
  DB_USER: user, DB_PASSWORD: password, COOKIE_SECURE: 'true',
  SERVER_ADDRESS: '0.0.0.0', PORT: '8080', SPRING_PROFILES_ACTIVE: 'cloud', REGISTRATION_ENABLED: 'false',
};
for (const [name, value] of Object.entries(variables)) {
  run(process.execPath, [vercelCli, 'env', 'add', name, 'production', '--yes', '--force', '--sensitive', '--scope', scope], { input: value });
  console.log(`Configured server-only variable: ${name}`);
}
console.log('Cloud database and production environment ready. No secrets were written to source files.');
