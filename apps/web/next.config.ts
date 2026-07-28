import path from "node:path";
import { fileURLToPath } from "node:url";
import type { NextConfig } from "next";

const API_ORIGIN = process.env.API_ORIGIN || "http://127.0.0.1:8080";

// Monorepo root — pnpm hoists shared deps to <root>/node_modules/.pnpm, so the standalone
// file tracer must walk up to the workspace root or it emits an incomplete node_modules.
const monorepoRoot = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Self-contained server bundle for the Docker image (docker/Dockerfile.web):
  // .next/standalone/apps/web/server.js + a pruned node_modules.
  output: "standalone",
  outputFileTracingRoot: monorepoRoot,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${API_ORIGIN}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
