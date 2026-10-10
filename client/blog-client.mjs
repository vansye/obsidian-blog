/** Dependency-free public read API client. baseUrl is the backend origin, not /api/v1. */
export class BlogApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `HTTP ${status}`);
    this.name = 'BlogApiError';
    this.status = status;
    this.code = problem?.code;
    this.problem = problem;
  }
}

export function createBlogClient({ baseUrl = '', fetch: fetcher = globalThis.fetch } = {}) {
  const base = baseUrl.replace(/\/+$/, '');
  const cache = new Map();
  async function request(path) {
    const url = `${base}/api/v1${path}`;
    const previous = cache.get(url);
    const headers = { Accept: 'application/json' };
    if (previous?.etag) headers['If-None-Match'] = previous.etag;
    const response = await fetcher(url, { headers, cache: 'no-cache', credentials: 'omit' });
    if (response.status === 304 && previous) return previous.body;
    if (!response.ok) {
      cache.delete(url);
      let problem;
      try { problem = await response.json(); } catch { /* A proxy may return a non-JSON error. */ }
      throw new BlogApiError(response.status, problem);
    }
    const body = await response.json();
    const etag = response.headers.get('ETag');
    if (etag) cache.set(url, { body, etag });
    else cache.delete(url);
    return body;
  }
  const paging = ({ page = 0, size = 20 } = {}) => `?${new URLSearchParams({ page, size })}`;
  const segment = value => encodeURIComponent(value);
  return {
    site: () => request('/site'),
    posts: options => request(`/posts${paging(options)}`),
    section: (section, options) => request(`/sections/${segment(section)}${paging(options)}`),
    post: path => request(`/posts/${segment(path)}`),
    neighbors: path => request(`/posts/${segment(path)}/neighbors`),
    about: () => request('/about'),
    links: () => request('/links'),
    clearCache: () => cache.clear(),
  };
}

/** Rewrite only the API's root-relative, content-addressed asset URLs. */
export function resolveAssetUrl(value, backendOrigin) {
  if (!/^\/assets\/[a-f0-9]{16}\.[a-z0-9]+$/.test(value)) return value;
  const origin = new URL(backendOrigin);
  if (!['http:', 'https:'].includes(origin.protocol)) throw new TypeError('Backend must use HTTP(S)');
  return new URL(value, origin.origin).href;
}

/** Browser DOM helper; call only for bodyHtml supplied by this backend. Not an HTML sanitizer. */
export function resolveAssetUrls(bodyHtml, backendOrigin, Parser = globalThis.DOMParser) {
  if (!Parser) throw new TypeError('resolveAssetUrls needs DOMParser; use a same-origin proxy for SSR.');
  const document = new Parser().parseFromString(bodyHtml, 'text/html');
  for (const image of document.querySelectorAll('img[src]')) {
    const source = image.getAttribute('src');
    const resolved = resolveAssetUrl(source, backendOrigin);
    if (resolved !== source) image.setAttribute('src', resolved);
  }
  return document.body.innerHTML;
}
