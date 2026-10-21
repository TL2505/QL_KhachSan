import http from 'node:http';
import https from 'node:https';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PUBLIC_DIR = path.join(__dirname, 'public');
const PORTAL_FILES = new Set(['portal.html', 'portal-app.js', 'portal.css', 'portal-customer.css', 'portal-theme.css']);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.pdf': 'application/pdf',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2'
};

export function createWebServer({ publicDir = PUBLIC_DIR, backendOrigin = process.env.HOTEL_API_ORIGIN || 'http://127.0.0.1:8081' } = {}) {
  const backend = new URL(backendOrigin);
  if (!['http:', 'https:'].includes(backend.protocol) || backend.username || backend.password || backend.pathname !== '/' || backend.search || backend.hash) {
    throw new Error('HOTEL_API_ORIGIN must be an HTTP(S) origin.');
  }

  return http.createServer(async (req, res) => {
    let pathname;
    try {
      pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    } catch {
      res.writeHead(400);
      res.end('Invalid URL');
      return;
    }

    // Reverse proxy /api/ requests to backend
    if (pathname === '/api/v1' || pathname.startsWith('/api/v1/')) {
      const target = new URL(req.url, backend);
      const headers = { ...req.headers, host: backend.host };
      delete headers.connection;
      headers['x-user-role'] = headers['x-user-role'] || 'guest';

      const upstream = (backend.protocol === 'https:' ? https : http).request(target, { method: req.method, headers }, reply => {
        res.writeHead(reply.statusCode, reply.headers);
        reply.pipe(res);
      });

      upstream.setTimeout(30000, () => upstream.destroy(new Error('timeout')));
      upstream.on('error', () => {
        if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'error', message: 'Service temporarily unavailable' }));
      });
      req.on('aborted', () => upstream.destroy());
      res.on('close', () => {
        if (!res.writableEnded) upstream.destroy();
      });
      req.pipe(upstream);
      return;
    }

    if (!['GET', 'HEAD'].includes(req.method)) {
      res.writeHead(405, { Allow: 'GET, HEAD' });
      res.end();
      return;
    }

    if (/[\\:\0]/.test(pathname) || pathname.split('/').some(part => part === '..')) {
      res.writeHead(400);
      res.end('Invalid path');
      return;
    }

    if (pathname === '/booking') {
      res.writeHead(302, { Location: '/booking/' });
      res.end();
      return;
    }

    if (pathname.startsWith('/booking/')) pathname = pathname.slice('/booking'.length);
    // The portal lives beside public/. Its old public/ placeholder redirects
    // to itself when public/ is mounted at the web origin.
    const portalFile = PORTAL_FILES.has(pathname.slice(1));
    const resourceDir = portalFile ? __dirname : publicDir;
    if (!portalFile && pathname.startsWith('/public/')) pathname = pathname.slice('/public'.length);
    const candidate = path.resolve(resourceDir, '.' + (pathname === '/' ? '/index.html' : pathname));

    try {
      const base = await fs.realpath(resourceDir);
      const resolved = await fs.realpath(candidate);
      const relative = path.relative(base, resolved);
      if (relative.startsWith('..') || path.isAbsolute(relative)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
      }
      const stat = await fs.stat(resolved);
      if (!stat.isFile()) {
        res.writeHead(404);
        res.end('Not found');
        return;
      }
      const content = await fs.readFile(resolved);
      res.writeHead(200, {
        'Content-Type': MIME[path.extname(resolved)] || 'application/octet-stream',
        'Content-Length': content.length,
        'X-Content-Type-Options': 'nosniff'
      });
      res.end(req.method === 'HEAD' ? undefined : content);
    } catch (error) {
      res.writeHead(error.code === 'ENOENT' ? 404 : 500);
      res.end('Unable to load this resource');
    }
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const server = createWebServer();
  const port = Number(process.env.PORT || 3000);
  server.listen(port, process.env.HOST || '0.0.0.0', () => {
    console.log(`\n======================================================`);
    console.log(` Aurelia Hotel Web UI is running!`);
    console.log(` Local URL:   http://localhost:${port}`);
    console.log(`======================================================\n`);
  });
}
