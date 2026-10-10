import { createBlogClient } from '/client/blog-client.mjs';
const api = createBlogClient();
const main = document.querySelector('main');
const element = (tag, text) => { const node = document.createElement(tag); if (text != null) node.textContent = text; return node; };
const link = (text, href) => { const node = element('a', text); node.href = href; return node; };
const sectionNames = { ARCHIVE: '归档', NOTES: '随笔', LAB: '项目' };
let revision = 0;
let site;

function body(post) {
  const article = element('article');
  article.append(element('h1', post.title));
  const metadata = [post.date, post.readMinutes > 0 ? `${post.readMinutes} 分钟` : null].filter(Boolean).join(' · ');
  if (metadata) article.append(element('p', metadata));
  const content = element('div'); content.className = 'prose';
  // Only accept HTML from this backend. Its renderer escapes source HTML.
  content.innerHTML = post.bodyHtml;
  article.append(content);
  return article;
}

async function render() {
  const current = ++revision;
  main.replaceChildren(element('p', '正在载入…'));
  try {
    site ||= await api.site();
    document.querySelector('#site-name').textContent = site.name;
    document.querySelector('nav').replaceChildren(...site.navigation.map(item => {
      const node = link(item.label, item.path);
      if (location.pathname === item.path) node.setAttribute('aria-current', 'page');
      return node;
    }));
    document.querySelector('#footer').textContent = `${site.author} · ${site.description}`;
    const output = document.createDocumentFragment();
    let title = site.name;
    if (location.pathname.startsWith('/p/')) {
      const path = decodeURIComponent(location.pathname.slice(3));
      const [post, neighbors] = await Promise.all([api.post(path), api.neighbors(path)]);
      title = post.title;
      output.append(body(post));
      const nav = element('nav'); nav.setAttribute('aria-label', '相邻文章');
      if (neighbors.newer) nav.append(link(`较新：${neighbors.newer.title}`, neighbors.newer.canonicalPath));
      if (neighbors.older) nav.append(link(`较早：${neighbors.older.title}`, neighbors.older.canonicalPath));
      output.append(nav);
    } else if (location.pathname === '/about') {
      title = '关于';
      const optional = promise => promise.catch(error => { if (error.status === 404) return null; throw error; });
      const [about, links] = await Promise.all([optional(api.about()), optional(api.links())]);
      if (about) output.append(body(about));
      else output.append(element('h1', '关于'), element('p', '还没有公开的介绍。'));
      if (links) { const section = body(links); const heading = section.querySelector('h1'); heading.replaceWith(element('h2', links.title)); output.append(section); }
    } else {
      const section = ({ '/': 'ARCHIVE', '/archive': 'ARCHIVE', '/notes': 'NOTES', '/lab': 'LAB' })[location.pathname];
      if (!section) throw { status: 404 };
      const requested = Number(new URLSearchParams(location.search).get('page') || 0);
      const page = await api.section(section, { page: requested, size: location.pathname === '/' ? 6 : 20 });
      title = location.pathname === '/' ? site.name : sectionNames[section];
      output.append(element('h1', title));
      if (location.pathname === '/') output.append(element('p', site.description));
      if (!page.items.length) output.append(element('p', '这里还没有公开内容。'));
      for (const post of page.items) {
        const item = element('article'); const heading = element('h2');
        heading.append(link(post.title, post.canonicalPath));
        item.append(element('time', post.date), heading, element('p', post.description || ''), element('small', post.tags.map(tag => `#${tag}`).join(' ')));
        output.append(item);
      }
      const pagination = element('nav'); pagination.setAttribute('aria-label', '分页');
      if (page.page > 0) pagination.append(link('上一页', `${location.pathname}?page=${page.page - 1}`));
      if ((page.page + 1) * page.size < page.total) pagination.append(link('下一页', `${location.pathname}?page=${page.page + 1}`));
      output.append(pagination);
    }
    if (current !== revision) return;
    document.title = `${title} · ${site.name}`;
    main.replaceChildren(output);
  } catch (error) {
    if (current !== revision) return;
    document.title = error.status === 404 ? `页面未找到 · ${site?.name || '博客'}` : '暂时无法载入';
    main.replaceChildren(element('h1', error.status === 404 ? '页面未找到' : '暂时无法载入'),
      element('p', error.status === 404 ? '内容不存在，或已不再公开。' : '请检查连接，稍后重试。'), link('返回首页', '/'));
    if (error.status !== 404) {
      const retry = element('button', '重试');
      retry.type = 'button';
      retry.addEventListener('click', render);
      main.append(retry);
    }
  }
}
document.addEventListener('click', event => {
  const anchor = event.target.closest('a');
  if (!anchor || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || anchor.target || anchor.hasAttribute('download')) return;
  const url = new URL(anchor.href);
  if (url.origin !== location.origin || url.hash || !(/^\/p\/[^/]+$/.test(url.pathname) || ['/', '/archive', '/notes', '/lab', '/about'].includes(url.pathname))) return;
  event.preventDefault(); history.pushState(null, '', url); render(); main.focus(); window.scrollTo(0, 0);
});
addEventListener('popstate', render);
render();
