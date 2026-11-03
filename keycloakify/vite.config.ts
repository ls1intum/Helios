import * as fs from "fs/promises";
import * as path from "path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { keycloakify } from "keycloakify/vite-plugin";

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        react(),
        tailwindcss(),
        keycloakify({
            themeName: "helios",
            accountThemeImplementation: "none",
            postBuild: async buildContext => {
                await fs.rm(
                    path.join(buildContext.keycloakifyBuildDirPath, ".gitignore")
                );
            }
        })
    ],
    resolve: {
        alias: {
            "@": path.resolve(import.meta.dirname, "./src")
        }
    }
});
