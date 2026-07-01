import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PORT = process.env.PORT || 3000;
const PUBLIC_DIR = path.join(__dirname, 'public');

const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.gif': 'image/gif',
    '.svg': 'image/svg+xml',
    '.pdf': 'application/pdf',
    '.ico': 'image/x-icon'
};

const server = http.createServer((req, res) => {
    // Safely resolve the requested file path, trimming any query parameters
    let basePath = req.url.split('?')[0];
    let filePath = path.join(PUBLIC_DIR, basePath === '/' ? 'index.html' : basePath);
    const extname = path.extname(filePath);
    let contentType = MIME_TYPES[extname] || 'application/octet-stream';

    // CORS headers for static resource requests if loaded cross-origin (though usually loaded same-origin)
    res.setHeader('Access-Control-Allow-Origin', '*');

    console.log(`[REQ] ${req.method} ${req.url} -> ${filePath}`);

    fs.readFile(filePath, (error, content) => {
        if (error) {
            if (error.code === 'ENOENT') {
                // If SPA route (no extension), serve index.html to let frontend JS handle the route
                if (!extname) {
                    fs.readFile(path.join(PUBLIC_DIR, 'index.html'), (err, indexContent) => {
                        if (err) {
                            res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
                            res.end('Error loading index.html');
                        } else {
                            res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
                            res.end(indexContent, 'utf-8');
                        }
                    });
                } else {
                    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
                    res.end('404 Not Found');
                }
            } else if (error.code === 'EISDIR') {
                res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
                res.end('404 Not Found');
            } else {
                res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
                res.end(`Server Error: ${error.code}`);
            }
        } else {
            res.writeHead(200, { 'Content-Type': contentType });
            res.end(content, 'utf-8');
        }
    });
});

server.listen(PORT, () => {
    console.log(`\n======================================================`);
    console.log(` Aurelia Hotel Web UI is running!`);
    console.log(` Local URL:   http://localhost:${PORT}`);
    console.log(`======================================================\n`);
});
