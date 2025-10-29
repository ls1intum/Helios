/**
 * Ensures CommonJS consumers receive a constructor when requiring `lru-cache`.
 * Newer major versions export `{ LRUCache }`, which breaks older code paths in
 * @babel/helper-compilation-targets that expect `module.exports` itself to be
 * constructible. We rewrite the cached export to provide a backward-compatible
 * function while preserving the original API surface.
 */
(() => {
  try {
    const cachePath = require.resolve('lru-cache');
    const mod = require(cachePath);
    if (typeof mod === 'function') {
      // Already compatible
      return;
    }
    const ctor = mod?.LRUCache;
    if (typeof ctor !== 'function') {
      return;
    }

    const compat = function (...args) {
      return new ctor(...args);
    };

    Object.assign(compat, mod);
    compat.prototype = ctor.prototype;

    require.cache[cachePath].exports = compat;
  } catch {
    // Silently ignore if lru-cache is not resolvable; Vitest will surface errors later.
  }
})();
