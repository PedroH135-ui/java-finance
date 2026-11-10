import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const script = new URL('./cloud-provision.mjs', import.meta.url);
for (const [label, project, scope] of [
  ['projeto ausente', '', 'example-team'],
  ['equipe ausente', 'example-project', ''],
  ['identificador inválido', '--unexpected-option', 'example-team'],
]) {
  test('provisionamento recusa ' + label + ' antes de chamar qualquer CLI', () => {
    const result = spawnSync(process.execPath, [fileURLToPath(script), 'missing-neon-cli.mjs', 'missing-vercel-cli.mjs'], {
      env: { ...process.env, NEON_PROJECT_ID: project, VERCEL_SCOPE: scope },
      encoding: 'utf8',
      windowsHide: true,
    });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Set NEON_PROJECT_ID and VERCEL_SCOPE privately/);
    assert.doesNotMatch(result.stderr, /MODULE_NOT_FOUND|ENOENT/);
  });
}

