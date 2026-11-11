import fs from 'node:fs';
import path from 'node:path';
import { env } from './env';

/**
 * Reads ../mock-data with the same rules as the server (FileMockDataRepository / MockDomain),
 * so the tests derive their expectations from the fixtures instead of hard-coding them.
 */

const SAFE_NAME = /^[A-Za-z0-9_-]{1,100}$/;
const MAX_DEPTH = 10;

export interface MockEndpoint {
  /** Folder path relative to mock-data, '/'-separated, e.g. "products/electronics". */
  folder: string;
  /** URL path served by WireMock, e.g. "/products/electronics". */
  path: string;
  /** Selector header, named after the top-level folder, e.g. "X-Products-Ref". */
  header: string;
}

export interface Fixture {
  endpoint: MockEndpoint;
  /** File name without extension; the selector header value. */
  reference: string;
  /** The JSON file content, which the server returns verbatim. */
  body: string;
  status: number;
  delayMs: number;
  contentType: string;
}

export function endpointFor(folder: string): MockEndpoint {
  const topLevel = folder.split('/')[0];
  return {
    folder,
    path: `/${folder}`,
    header: `X-${topLevel.charAt(0).toUpperCase()}${topLevel.slice(1)}-Ref`,
  };
}

/** All folders that the server exposes as endpoints, sorted like the server sorts them. */
export function discoverEndpoints(baseDir = env.mockDataDir): MockEndpoint[] {
  const folders: string[] = [];
  const walk = (dir: string, segments: string[]) => {
    if (segments.length >= MAX_DEPTH) return;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (!entry.isDirectory() || !SAFE_NAME.test(entry.name)) continue;
      const next = [...segments, entry.name];
      folders.push(next.join('/'));
      walk(path.join(dir, entry.name), next);
    }
  };
  walk(baseDir, []);
  return folders.sort().map(endpointFor);
}

/** Every servable fixture (<reference>.json + optional <reference>.properties) of every endpoint. */
export function discoverFixtures(baseDir = env.mockDataDir): Fixture[] {
  return discoverEndpoints(baseDir).flatMap((endpoint) => {
    const dir = path.join(baseDir, ...endpoint.folder.split('/'));
    return fs
      .readdirSync(dir, { withFileTypes: true })
      .filter((f) => f.isFile() && f.name.endsWith('.json'))
      .map((f) => f.name.slice(0, -'.json'.length))
      .filter((reference) => SAFE_NAME.test(reference))
      .sort()
      .map((reference) => {
        const meta = readProperties(path.join(dir, `${reference}.properties`));
        return {
          endpoint,
          reference,
          body: fs.readFileSync(path.join(dir, `${reference}.json`), 'utf8'),
          status: Number(meta['status'] ?? 200),
          delayMs: Number(meta['delay-ms'] ?? 0),
          contentType: meta['content-type'] ?? 'application/json',
        };
      });
  });
}

/** Minimal java.util.Properties reader: key=value or key:value lines, # and ! comments. */
function readProperties(file: string): Record<string, string> {
  if (!fs.existsSync(file)) return {};
  const result: Record<string, string> = {};
  for (const raw of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    const match = /^([^=:\s]+)\s*[=:\s]\s*(.*)$/.exec(line);
    if (match) result[match[1]] = match[2].trim();
  }
  return result;
}

/** A reference that has a fixture in none of the given folders. */
export function unknownReference(): string {
  return 'does-not-exist-0000';
}
