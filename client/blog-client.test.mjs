import test from 'node:test';
import assert from 'node:assert/strict';
import { createBlogClient, BlogApiError, resolveAssetUrl, resolveAssetUrls } from './blog-client.mjs';

const json = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), { status, headers });

test('all read routes, URL encoding and zero-based pagination', async () => {
  const calls = [];
  const client = createBlogClient({ baseUrl: 'https://blog.example/', fetch: async (url, options) => {
    calls.push([url, options]); return json({ items: [], total: 0, page: 0, size: 20 });
  } });
  await client.site(); await client.posts(); await client.section('NOTES', { page: 2, size: 5 });
  await client.post('带 空格'); await client.neighbors('a/b'); await client.about(); await client.links();
  assert.deepEqual(calls.map(([url]) => url), [
    'https://blog.example/api/v1/site', 'https://blog.example/api/v1/posts?page=0&size=20',
    'https://blog.example/api/v1/sections/NOTES?page=2&size=5',
    'https://blog.example/api/v1/posts/%E5%B8%A6%20%E7%A9%BA%E6%A0%BC',
    'https://blog.example/api/v1/posts/a%2Fb/neighbors',
    'https://blog.example/api/v1/about', 'https://blog.example/api/v1/links',
  ]);
  assert.equal(calls[0][1].credentials, 'omit');
  assert.equal(calls[0][1].cache, 'no-cache');
});

test('ETag revalidation reuses 304 body, then reflects unpublication and drops old cache', async () => {
  const requests = [];
  const responses = [json({ title: 'Visible' }, 200, { ETag: '"v1"' }), new Response(null, { status: 304 }),
    json({ status: 404, code: 'NOT_FOUND', detail: 'Not found' }, 404), json({ title: 'Republished' })];
  const client = createBlogClient({ fetch: async (_, options) => { requests.push(options); return responses.shift(); } });
  assert.equal((await client.post('hello')).title, 'Visible');
  assert.equal((await client.post('hello')).title, 'Visible');
  assert.equal(requests[1].headers['If-None-Match'], '"v1"');
  await assert.rejects(client.post('hello'), error => error instanceof BlogApiError && error.status === 404 && error.code === 'NOT_FOUND');
  assert.equal((await client.post('hello')).title, 'Republished');
  assert.equal(requests[3].headers['If-None-Match'], undefined);
});

test('clearCache stops conditional requests and cache entries remain isolated by URL', async () => {
  const requests = [];
  const client = createBlogClient({ fetch: async (url, options) => {
    requests.push(options.headers); return json({ url }, 200, { ETag: '"x"' });
  } });
  await client.post('first'); await client.post('second'); client.clearCache(); await client.post('first');
  assert.ok(requests.every(headers => headers['If-None-Match'] === undefined));
});

test('empty page is a successful value, malformed pagination remains an API error', async () => {
  const empty = { items: [], total: 0, page: 0, size: 20 };
  const client = createBlogClient({ fetch: async url => url.includes('page=-1')
    ? json({ title: 'Bad request', status: 400, code: 'INVALID_ARGUMENT' }, 400) : json(empty) });
  assert.deepEqual(await client.posts(), empty);
  await assert.rejects(client.posts({ page: -1 }), error => error.status === 400 && error.code === 'INVALID_ARGUMENT');
});

test('non-JSON server failures and network failures remain distinguishable', async () => {
  const client = createBlogClient({ fetch: async () => new Response('gateway down', { status: 502 }) });
  await assert.rejects(client.site(), error => error.status === 502 && error.message === 'HTTP 502');
  const network = new TypeError('network offline');
  const offline = createBlogClient({ fetch: async () => { throw network; } });
  await assert.rejects(offline.site(), error => error === network);
});

test('cross-origin asset conversion only touches actual backend asset paths', () => {
  const origin = 'https://api.example:8443/ignored';
  assert.equal(resolveAssetUrl('/assets/0123456789abcdef.svg', origin), 'https://api.example:8443/assets/0123456789abcdef.svg');
  for (const url of ['https://elsewhere.example/a.png', '//elsewhere.example/a.png', 'data:image/png;base64,AA',
    '/assets/../private', '/assets/anything.svg', '/p/hello', 'relative.png', '#anchor']) {
    assert.equal(resolveAssetUrl(url, origin), url);
  }
  assert.throws(() => resolveAssetUrl('/assets/0123456789abcdef.svg', 'file:///tmp/'), TypeError);
});

test('SSR receives an explicit DOM requirement instead of silently rewriting HTML text', () => {
  assert.throws(() => resolveAssetUrls('<p>hello</p>', 'https://api.example', null), /DOMParser/);
});
