import { Pipe, PipeTransform, SecurityContext, inject } from '@angular/core';
import { marked } from 'marked';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({
  name: 'markdown',
  standalone: true,
})
export class MarkdownPipe implements PipeTransform {
  private sanitizer = inject(DomSanitizer);

  constructor() {
    // Configure marked renderer
    const renderer = new marked.Renderer();
    renderer.code = code => {
      return `<span style="background-color: rgb(243 244 246); padding: 2px 8px; border-radius: 4px; font-family: monospace;">${code.text}</span>`;
    };
    renderer.codespan = code => {
      return `<span style="background-color: rgb(243 244 246); padding: 2px 6px; border-radius: 4px; font-family: monospace;">${code.text}</span>`;
    };
    renderer.heading = ({ text, depth }) => {
      const id = text.toLowerCase().replace(/\s+/g, '-');
      return `<h${depth} id="${id}" style="font-size: 1.5em; font-weight: bold;">${text}</h${depth}>`;
    };
    renderer.listitem = ({ text }) => {
      return `   • ${text}<br>`;
    };
    renderer.blockquote = text => {
      return `<blockquote style="border-left: 4px solid #ccc; padding-left: 16px; color: #555;">${text}</blockquote>`;
    };
    marked.setOptions({ renderer });
  }

  private hasMarkdownSyntax(text: string): boolean {
    // Common markdown patterns
    const markdownPatterns = [
      /`[^`]+`/, // inline code
      /\*\*[^*]+\*\*/, // bold
      /\*[^*]+\*/, // italic
      /\[[^\]]+\]\([^)]+\)/, // links
      /```[\s\S]*?```/, // code blocks
      /#{1,6}\s.+/, // headers
    ];

    return markdownPatterns.some(pattern => pattern.test(text));
  }

  transform(value: string | undefined): SafeHtml {
    if (!value) return '';
    if (this.hasMarkdownSyntax(value)) {
      const html = marked.parse(value).toString();
      return this.sanitizer.bypassSecurityTrustHtml(html || '');
    }
    return this.sanitizer.sanitize(SecurityContext.HTML, value) || '';
  }
}
