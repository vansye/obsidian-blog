import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, copyFileSync, readdirSync, readFileSync, writeFileSync, rmSync, realpathSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const source = fileURLToPath(new URL('../', import.meta.url));
function copyDirectory(from, to) {
  mkdirSync(to);
  for (const entry of readdirSync(from, { withFileTypes: true })) {
    if (entry.isDirectory()) copyDirectory(join(from, entry.name), join(to, entry.name));
    else copyFileSync(join(from, entry.name), join(to, entry.name));
  }
}
const shell = process.env.SETUP_SHELL || (process.platform === 'win32' ? 'powershell.exe' : 'sh');
const isPowerShell = /(?:powershell|pwsh)(?:\.exe)?$/i.test(shell);
const runSetup = directory => spawnSync(shell, isPowerShell
  ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', join(directory, 'scripts/setup-demo.ps1'), '-PrepareOnly']
  : [join(directory, 'scripts/setup-demo.sh'), '--prepare-only'], { cwd: directory, encoding: 'utf8' });

for (const existingEnv of [false, true]) {
  test(`demo setup creates a Git checkout, refuses overwrite and ${existingEnv ? 'preserves existing' : 'generates unique'} credentials`, () => {
    const directory = mkdtempSync(join(tmpdir(), 'obsidian-blog-setup-'));
    const ownedPath = realpathSync(directory);
    try {
      mkdirSync(join(directory, 'scripts'));
      copyFileSync(join(source, 'scripts/setup-demo.ps1'), join(directory, 'scripts/setup-demo.ps1'));
      copyFileSync(join(source, 'scripts/setup-demo.sh'), join(directory, 'scripts/setup-demo.sh'));
      copyDirectory(join(source, 'example-vault'), join(directory, 'example-vault'));
      if (existingEnv) writeFileSync(join(directory, '.env'), 'KEEP_THIS_EXACTLY=yes\n');
      const result = runSetup(directory);
      assert.equal(result.status, 0, result.stdout + result.stderr);
      const env = readFileSync(join(directory, '.env'), 'utf8');
      if (existingEnv) assert.equal(env, 'KEEP_THIS_EXACTLY=yes\n');
      else {
        assert.match(env, /PUBLISH_ROOT=博客/);
        const secrets = [...env.matchAll(/^(?:DB_PASSWORD|MYSQL_ROOT_PASSWORD|REDIS_PASSWORD|VAULT_WEBHOOK_SECRET)=([a-f0-9]{48})$/gm)].map(match => match[1]);
        assert.equal(secrets.length, 4);
        assert.equal(new Set(secrets).size, 4);
      }
      const vault = join(directory, 'data/vault');
      assert.ok(existsSync(join(vault, '博客/文章/从一篇笔记开始.md')));
      const git = (...args) => spawnSync('git', ['-C', vault, ...args], { encoding: 'utf8' });
      assert.equal(git('rev-list', '--count', 'HEAD').stdout.trim(), '1');
      assert.equal(git('status', '--porcelain').stdout.trim(), '');
      const head = git('rev-parse', 'HEAD').stdout;
      const again = runSetup(directory);
      assert.notEqual(again.status, 0);
      assert.match(again.stdout + again.stderr, /Refusing to overwrite/);
      assert.equal(readFileSync(join(directory, '.env'), 'utf8'), env);
      assert.equal(git('rev-parse', 'HEAD').stdout, head);
    } finally {
      // Delete only the exact temporary directory created by this test.
      assert.equal(realpathSync(directory), ownedPath);
      assert.ok(resolve(ownedPath).startsWith(resolve(realpathSync(tmpdir())) + sep));
      rmSync(ownedPath, { recursive: true });
    }
  });
}
