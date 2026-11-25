import type { NextConfig } from "next";

const config: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  productionBrowserSourceMaps: false,
  images: { unoptimized: true },
  // This folder is the workspace root: don't let lockfiles elsewhere change file tracing.
  outputFileTracingRoot: import.meta.dirname,
  turbopack: { root: import.meta.dirname },
  experimental: {
    authInterrupts: true, // forbidden()
    // ISR entries stay in memory, so the container can run with a read-only file system.
    isrFlushToDisk: false,
    // Invoice uploads: 10 MB file (the backend's limit) + form fields.
    serverActions: { bodySizeLimit: "11mb" },
    proxyClientMaxBodySize: "11mb",
  },
  async redirects() {
    return [{ source: "/catalog", destination: "/catalog/1", permanent: false }];
  },
};

export default config;
