// Umi history uses query-string 6 (CommonJS). The patched decoder is ESM-only.
// Preserve its callable CommonJS API while using the security-fixed implementation.
module.exports = require('decode-uri-component/index.js').default;
