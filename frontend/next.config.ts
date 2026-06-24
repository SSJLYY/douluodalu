import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  allowedDevOrigins: ["*.monkeycode-ai.online"],
  turbopack: {
    root: path.parse(path.resolve(__dirname)).root,
  },
};

export default nextConfig;
