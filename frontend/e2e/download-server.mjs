// Native browser downloads bypass Playwright request routing. This isolated HTTP
// fixture verifies cookies and attachment bytes without a live backend or MinIO.
import { createServer } from 'node:http';
const downloads = new Set();
createServer((request, response) => {
  if (request.url === '/health') { response.end('ok'); return; }
  if (request.url?.startsWith('/downloads/')) {
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ downloaded: downloads.has(decodeURIComponent(request.url.slice('/downloads/'.length))) })); return;
  }
  if (request.url === '/api/applications/1/update-package/download') {
    const session = /(?:^|;\s*)JSESSIONID=([^;]+)/.exec(request.headers.cookie ?? '')?.[1];
    if (!session?.startsWith('mock-owner-')) { response.writeHead(401); response.end(); return; }
    downloads.add(session);
    response.writeHead(200, { 'Content-Type': 'application/octet-stream', 'Content-Disposition': 'attachment; filename=release.zip', 'Cache-Control': 'no-store' });
    response.end('archive contents'); return;
  }
  response.writeHead(404); response.end();
}).listen(5188, '127.0.0.1');
