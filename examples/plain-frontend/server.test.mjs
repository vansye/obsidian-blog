import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';

test('example server serves deep routes, limits files, proxies API/assets and preserves conditional caching', async () => {
  const requests = [];
  const backend = http.createServer((request, response) => {
    requests.push({ path: request.url, etag: request.headers['if-none-match'] });
    if (request.headers['if-none-match'] === '"demo"') {
      response.writeHead(304, { ETag: '"demo"', 'Cache-Control': 'no-cache' }).end();
    } else {
      response.writeHead(200, { 'Content-Type': 'application/json', ETag: '"demo"', 'Cache-Control': 'no-cache' });
      response.end(JSON.stringify({ path: request.url }));
    }
  });
  backend.listen(0, '127.0.0.1');
  await once(backend, 'listening');
  const child = spawn(process.execPath, [fileURLToPath(new URL('./server.mjs', import.meta.url))], {
    env: { ...process.env, FRONTEND_PORT: '0', BLOG_BACKEND: `http://127.0.0.1:${backend.address().port}` },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  const exited = once(child, 'exit').catch(() => {});
  try {
    const origin = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Frontend did not start')), 5000);
      child.once('error', error => { clearTimeout(timer); reject(error); });
      child.once('exit', code => { clearTimeout(timer); reject(new Error(`Frontend exited: ${code}`)); });
      child.stdout.on('data', data => {
        const match = data.toString().match(/Frontend: (http:\/\/127\.0\.0\.1:\d+)/);
        if (match) { clearTimeout(timer); resolve(match[1]); }
      });
    });
    assert.match(await (await fetch(`${origin}/p/start-with-a-note`)).text(), /<main id="content"/);
    assert.equal((await fetch(`${origin}/client/blog-client.mjs`)).status, 200);
    assert.equal((await fetch(`${origin}/.env`)).status, 404);
    assert.equal((await fetch(`${origin}/data/vault/README.md`)).status, 404);
    assert.equal((await fetch(`${origin}/api/v1/posts`, { method: 'POST' })).status, 405);
    const api = await fetch(`${origin}/api/v1/posts?page=2&size=5`);
    assert.deepEqual(await api.json(), { path: '/api/v1/posts?page=2&size=5' });
    assert.equal(api.headers.get('etag'), '"demo"');
    const conditional = await fetch(`${origin}/assets/0123456789abcdef.svg`, { headers: { 'If-None-Match': '"demo"' } });
    assert.equal(conditional.status, 304);
    assert.equal(conditional.headers.get('cache-control'), 'no-cache');
    assert.deepEqual(requests, [{ path: '/api/v1/posts?page=2&size=5', etag: undefined }, { path: '/assets/0123456789abcdef.svg', etag: '"demo"' }]);
    const malformedStatus = await new Promise((resolve, reject) => {
      const address = new URL(origin);
      const request = http.get({ hostname: address.hostname, port: address.port, path: '//[' }, response => {
        response.resume();
        response.once('end', () => resolve(response.statusCode));
      });
      request.once('error', reject);
    });
    assert.equal(malformedStatus, 400);
    assert.equal((await fetch(`${origin}/`)).status, 200);
  } finally {
    child.kill();
    await exited;
    backend.closeAllConnections();
    await new Promise(resolve => backend.close(resolve));
  }
});
