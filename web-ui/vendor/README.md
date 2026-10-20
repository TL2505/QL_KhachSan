# Local QR decoding

`jsQR.js` contains jsQR 1.4.0 with a small ES module wrapper around the upstream CommonJS bundle. It is loaded on demand and decodes images inside the browser without an external request containing the image.

- Upstream: https://github.com/cozmo/jsQR
- Pinned bundle: https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.js
- License: Apache-2.0, reproduced in `jsQR.LICENSE`.

The served copy is in `web-ui/public/vendor/`; keep both copies synchronized.
