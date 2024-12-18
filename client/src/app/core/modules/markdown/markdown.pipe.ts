import { Pipe, PipeTransform, SecurityContext } from '@angular/core';
import { marked } from 'marked';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({
  name: 'markdown',
  standalone: true,
})
export class MarkdownPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {
    // Configure marked renderer
    const renderer = new marked.Renderer();
    renderer.code = code => {
      return `<span style="background-color: rgb(243 244 246); padding: 2px 8px; border-radius: 4px; font-family: monospace;">${code.text}</span>`;
    };
    renderer.codespan = code => {
      return `<span style="background-color: rgb(243 244 246); padding: 2px 6px; border-radius: 4px; font-family: monospace;">${code.text}</span>`;
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

  transform(value: string): SafeHtml {
    if (!value) return '';
    if (this.hasMarkdownSyntax(value)) {
      const html = marked.parse(value).toString();
      return this.sanitizer.bypassSecurityTrustHtml(html || '');
    }
    return this.sanitizer.sanitize(SecurityContext.HTML, value) || '';
  }
}
