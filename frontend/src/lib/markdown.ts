import { marked } from "marked";
import sanitizeHtml from "sanitize-html";
import TurndownService from "turndown";

marked.setOptions({
  breaks: true,
  gfm: true,
});

const turndownService = new TurndownService({
  codeBlockStyle: "fenced",
  emDelimiter: "_",
  headingStyle: "atx",
  bulletListMarker: "-",
});

const MAX_CACHE_ENTRIES = 200;
export interface TocHeading {
  id: string;
  text: string;
  level: number;
}

export interface MarkdownRenderResult {
  html: string;
  toc: TocHeading[];
  readingTimeMinutes: number;
}

const markdownRenderCache = new Map<string, MarkdownRenderResult>();
const htmlToMarkdownCache = new Map<string, string>();

function cacheGet<T>(cache: Map<string, T>, key: string): T | undefined {
  const value = cache.get(key);
  if (value === undefined) {
    return undefined;
  }

  // Refresh insertion order for a simple LRU behavior.
  cache.delete(key);
  cache.set(key, value);
  return value;
}

function cacheSet<T>(
  cache: Map<string, T>,
  key: string,
  value: T,
): void {
  cache.set(key, value);
  if (cache.size <= MAX_CACHE_ENTRIES) {
    return;
  }

  const oldestKey = cache.keys().next().value;
  if (oldestKey !== undefined) {
    cache.delete(oldestKey);
  }
}

export function markdownToHtml(markdown: string): string {
  return renderMarkdown(markdown).html;
}

function stripHtml(value: string): string {
  return value.replace(/<[^>]*>/g, "").trim();
}

function slugifyHeading(value: string): string {
  const normalized = value
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, "")
    .trim()
    .replace(/\s+/g, "-");
  return normalized || "section";
}

function estimateReadingTimeMinutes(markdown: string): number {
  const cleanText = markdown
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`[^`]*`/g, " ")
    .replace(/[#>*_~\-\[\]()]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  if (!cleanText) {
    return 1;
  }

  const words = cleanText.split(" ").length;
  return Math.max(1, Math.ceil(words / 220));
}

export function renderMarkdown(markdown: string): MarkdownRenderResult {
  const cached = cacheGet(markdownRenderCache, markdown);
  if (cached !== undefined) {
    return cached;
  }

  const headingCounts = new Map<string, number>();
  const toc: TocHeading[] = [];

  let unsafeHtml = marked.parse(markdown) as string;
  unsafeHtml = unsafeHtml.replace(
    /<h([1-6])([^>]*)>([\s\S]*?)<\/h\1>/gi,
    (_match, levelRaw: string, attrs: string, content: string) => {
      const level = Number(levelRaw);
      const plainText = stripHtml(content);
      const baseId = slugifyHeading(plainText);
      const duplicateCount = headingCounts.get(baseId) ?? 0;
      headingCounts.set(baseId, duplicateCount + 1);
      const id = duplicateCount === 0 ? baseId : `${baseId}-${duplicateCount + 1}`;

      if (level >= 2 && level <= 3) {
        toc.push({ id, text: plainText, level });
      }

      return `<h${level}${attrs} id="${id}">${content}</h${level}>`;
    },
  );

  const sanitizedHtml = sanitizeHtml(unsafeHtml, {
    allowedTags: sanitizeHtml.defaults.allowedTags.concat([
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "img",
    ]),
    allowedAttributes: {
      a: ["href", "name", "target", "rel"],
      img: ["src", "alt", "title"],
      code: ["class"],
      pre: ["class"],
      span: ["class"],
      h1: ["id"],
      h2: ["id"],
      h3: ["id"],
      h4: ["id"],
      h5: ["id"],
      h6: ["id"],
    },
    allowedSchemes: ["http", "https", "mailto"],
  });

  const result: MarkdownRenderResult = {
    html: sanitizedHtml,
    toc,
    readingTimeMinutes: estimateReadingTimeMinutes(markdown),
  };

  cacheSet(markdownRenderCache, markdown, result);
  return result;
}

export function htmlToMarkdown(html: string): string {
  const cached = cacheGet(htmlToMarkdownCache, html);
  if (cached !== undefined) {
    return cached;
  }

  const markdown = turndownService.turndown(html).trim();
  cacheSet(htmlToMarkdownCache, html, markdown);
  return markdown;
}
