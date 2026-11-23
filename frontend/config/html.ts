import type { IApi } from '@umijs/max';
import { structuredData } from './siteMetadata';

export default function html(api: IApi) {
  api.modifyHTML($ => {
    $('html').attr('lang', 'zh-CN');
    $('meta[name="viewport"]').attr('content', 'width=device-width, initial-scale=1.0');
    $('head').append('<link rel="canonical" href="https://ciphergate.ayssu.com/">');
    $('head').append(`<script type="application/ld+json">${JSON.stringify(structuredData)}</script>`);
    return $;
  });
}
