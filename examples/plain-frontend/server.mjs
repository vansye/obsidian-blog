import http from 'node:http';
import https from 'node:https';
import { readFile } from 'node:fs/promises';

const backend = new URL(process.env.BLOG_BACKEND || 'http://127.0.0.1:8080');
if (!['http:', 'https:'].includes(backend.protocol)) throw new Error('BLOG_BACKEND must use HTTP(S)');
const files = new Map([
  ['/app.mjs', [new URL('./app.mjs', import.meta.url), 'text/javascript; charset=utf-8']],
  ['/style.css', [new URL('./style.css', import.meta.url), 'text/css; charset=utf-8']],
  ['/client/blog-client.mjs', [new URL('../../client/blog-client.mjs', import.meta.url), 'text/javascript; charset=utf-8']],
]);
const server = http.createServer(async (request, response) => {
  if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405, { Allow: 'GET, HEAD' }).end(); return; }
  let url;
  try {
    url = new URL(request.url, 'http://localhost');
  } catch {
    response.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Bad request');
    return;
  }
  if (url.pathname.startsWith('/api/v1/') || url.pathname.startsWith('/assets/')) {
    const transport = backend.protocol === 'https:' ? https : http;
    const upstream = transport.request(new URL(url.pathname + url.search, backend.origin), {
      method: request.method,
      headers: { Accept: request.headers.accept || '*/*', ...(request.headers['if-none-match'] ? { 'If-None-Match': request.headers['if-none-match'] } : {}) },
    }, incoming => {
      response.writeHead(incoming.statusCode, incoming.headers);
      incoming.pipe(response);
    });
    upstream.on('error', () => {
      if (!response.headersSent) response.writeHead(502, { 'Content-Type': 'application/problem+json' });
      response.end(JSON.stringify({ status: 502, title: 'Backend unavailable' }));
    });
    upstream.end();
    return;
  }
  const entry = files.get(url.pathname);
  const isPage = ['/', '/archive', '/notes', '/lab', '/about'].includes(url.pathname) || /^\/p\/[^/]+$/.test(url.pathname);
  if (!entry && !isPage) { response.writeHead(404).end('Not found'); return; }
  try {
    const [file, type] = entry || [new URL('./index.html', import.meta.url), 'text/html; charset=utf-8'];
    const body = await readFile(file);
    response.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-cache' });
    response.end(request.method === 'HEAD' ? undefined : body);
  } catch { response.writeHead(500).end('Cannot read frontend file'); }
});
server.listen(Number(process.env.FRONTEND_PORT || 5173), '127.0.0.1', () => {
  console.log(`Frontend: http://127.0.0.1:${server.address().port}; backend: ${backend.origin}`);
});
