// Fails the build when the main bundle grows past the spec's budget (§10: ≤ 400 KB gzipped).
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

const Budget = 400_000;
const dir = path.resolve(import.meta.dirname, "../dist/assets");
const [file, size] = fs
  .readdirSync(dir)
  .filter((f) => f.endsWith(".js"))
  .map((f) => [f, zlib.gzipSync(fs.readFileSync(path.join(dir, f))).length])
  .reduce((a, b) => (b[1] > a[1] ? b : a));
console.log(`main bundle ${file}: ${size} bytes gzipped (budget ${Budget})`);
if (size > Budget) {
  console.error(`over budget by ${size - Budget} bytes`);
  process.exit(1);
}
