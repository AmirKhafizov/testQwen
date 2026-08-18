package com.construction.costmonitor.infrastructure.price;

import com.construction.costmonitor.config.AppProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Реальный парсер LemanaPro (Playwright).
 * Часто блокируется Qrator (HTTP 403) — тогда используйте app.lemana.provider=mock.
 */
@Component
@ConditionalOnProperty(name = "app.lemana.provider", havingValue = "parser", matchIfMissing = true)
public class LemanaProParserProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(LemanaProParserProvider.class);
    public static final String SOURCE = "LEMANA_PRO_PARSER";

    private static final String STEALTH_INIT = """
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
            Object.defineProperty(navigator, 'languages', { get: () => ['ru-RU', 'ru', 'en-US', 'en'] });
            Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
            window.chrome = { runtime: {} };
            """;

    private final AppProperties appProperties;
    private final ReentrantLock lock = new ReentrantLock();

    private Playwright playwright;
    private Browser browser;

    public LemanaProParserProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public String getSourceName() {
        return SOURCE;
    }

    @Override
    public Optional<PriceQuote> fetchPrice(String externalSku) {
        if (externalSku == null || externalSku.isBlank()) {
            log.warn("Парсер LemanaPro: пустой артикул, запрос пропущен");
            return Optional.empty();
        }

        String sku = externalSku.trim();
        log.info("Парсер LemanaPro: начинаем получение цены для артикула [{}]", sku);

        lock.lock();
        try {
            ensureBrowser();
            delay();

            String base = trimSlash(appProperties.getLemana().getBaseUrl());

            try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale("ru-RU")
                    .setTimezoneId("Europe/Moscow")
                    .setUserAgent(appProperties.getLemana().getUserAgent())
                    .setViewportSize(1440, 900)
                    .setJavaScriptEnabled(true)
                    .setExtraHTTPHeaders(java.util.Map.of(
                            "Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
                            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                    )))) {

                context.addInitScript(STEALTH_INIT);
                Page page = context.newPage();
                page.setDefaultTimeout(appProperties.getLemana().getNavigationTimeoutMs());

                String encoded = URLEncoder.encode(sku, StandardCharsets.UTF_8);
                List<String> searchUrls = List.of(
                        base + "/search/?q=" + encoded,
                        "https://lemanapro.ru/search/?q=" + encoded
                );

                String productUrl = null;
                boolean saw403 = false;

                for (String searchUrl : searchUrls) {
                    log.info("Парсер LemanaPro: открываем поиск — {}", searchUrl);
                    navigateAndWaitQrator(page, searchUrl);
                    diagnosePage(page, sku);

                    if (isHttp403(page)) {
                        saw403 = true;
                        log.warn("Парсер LemanaPro: HTTP 403 (Qrator). headless={}, channel='{}'",
                                appProperties.getLemana().isHeadless(),
                                appProperties.getLemana().getBrowserChannel());
                        continue;
                    }

                    productUrl = findProductUrl(page, sku, base);
                    if (productUrl != null) {
                        break;
                    }
                }

                if (productUrl == null) {
                    productUrl = tryOpenProductBySkuPatterns(page, base, sku);
                }

                if (productUrl == null) {
                    if (saw403) {
                        log.error("Парсер LemanaPro: сайт отвечает 403. " +
                                "Для разработки включите mock: app.lemana.provider=mock. " +
                                "Долгосрочно: B2B API / прокси / другая сеть.");
                    } else {
                        log.warn("Парсер LemanaPro: товар [{}] не найден", sku);
                    }
                    return Optional.empty();
                }

                log.info("Парсер LemanaPro: открываем карточку — {}", productUrl);
                navigateAndWaitQrator(page, productUrl);
                diagnosePage(page, sku);

                if (isHttp403(page)) {
                    log.error("Парсер LemanaPro: 403 на карточке. Включите app.lemana.provider=mock для разработки.");
                    return Optional.empty();
                }

                String html = page.content();
                Optional<PriceHtmlParser.ParsedProduct> parsed =
                        PriceHtmlParser.parse(html, sku, page.url());

                if (parsed.isEmpty()) {
                    page.waitForTimeout(3000);
                    parsed = PriceHtmlParser.parse(page.content(), sku, page.url());
                }

                if (parsed.isEmpty()) {
                    log.warn("Парсер LemanaPro: цена не извлечена, url={}", page.url());
                    return Optional.empty();
                }

                PriceHtmlParser.ParsedProduct p = parsed.get();
                log.info("Парсер LemanaPro: цена получена — [{}] «{}» {} {}",
                        sku, p.productName(), p.price(), p.currency());

                return Optional.of(new PriceQuote(
                        p.externalSku(),
                        p.productName(),
                        p.price(),
                        p.currency(),
                        page.url()
                ));
            }
        } catch (Exception e) {
            log.error("Парсер LemanaPro: ошибка для [{}]: {}", sku, e.getMessage(), e);
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    private void navigateAndWaitQrator(Page page, String url) {
        page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

        long waitMs = appProperties.getLemana().getQratorWaitMs();
        long step = 1500;
        long waited = 0;

        while (waited < waitMs) {
            page.waitForTimeout(step);
            waited += step;
            if (!isHttp403(page) && !looksLikeBareQrator(page)) {
                log.info("Парсер LemanaPro: страница загружена после ~{} мс (title=«{}»)",
                        waited, safeTitle(page));
                return;
            }
            log.debug("Парсер LemanaPro: ждём Qrator… {}/{} мс, title=«{}»",
                    waited, waitMs, safeTitle(page));
        }
        log.warn("Парсер LemanaPro: таймаут Qrator ({} мс), title=«{}»", waitMs, safeTitle(page));
    }

    private boolean isHttp403(Page page) {
        try {
            String title = page.title();
            return title != null && title.toUpperCase().contains("403");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean looksLikeBareQrator(Page page) {
        try {
            Object len = page.evaluate("() => document.body ? document.body.innerText.length : 0");
            int bodyLen = len instanceof Number n ? n.intValue() : 0;
            String html = page.content();
            boolean hasQrator = html != null && (html.contains("__qrator") || html.contains("qauth.js"));
            return hasQrator && bodyLen < 100;
        } catch (Exception e) {
            return false;
        }
    }

    private String safeTitle(Page page) {
        try {
            return page.title();
        } catch (Exception e) {
            return "?";
        }
    }

    private void diagnosePage(Page page, String sku) {
        try {
            Object stats = page.evaluate("""
                    (sku) => {
                      const links = Array.from(document.querySelectorAll('a[href]')).map(a => a.href);
                      const withSku = links.filter(h => h && h.includes(sku));
                      return {
                        linkCount: links.length,
                        withSkuCount: withSku.length,
                        bodyTextLen: document.body ? document.body.innerText.length : 0
                      };
                    }
                    """, sku);
            log.info("Парсер LemanaPro: диагностика title=«{}» url={} stats={}",
                    page.title(), page.url(), stats);
        } catch (Exception e) {
            log.debug("Диагностика: {}", e.getMessage());
        }
    }

    private String findProductUrl(Page page, String sku, String base) {
        Object result = page.evaluate("""
                (sku) => {
                  const links = Array.from(document.querySelectorAll('a[href]')).map(a => a.href);
                  const preferred = links.filter(h => h && h.includes(sku) && h.includes('/product/'));
                  if (preferred.length) return preferred[0];
                  const any = links.filter(h => h && h.includes(sku));
                  return any.length ? any[0] : null;
                }
                """, sku);

        if (result instanceof String s && !s.isBlank()) {
            log.info("Парсер LemanaPro: ссылка из DOM — {}", s);
            return s.startsWith("http") ? s : base + s;
        }

        Document doc = Jsoup.parse(page.content(), page.url());
        Set<String> candidates = new LinkedHashSet<>();
        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            if (href != null && href.contains(sku)) {
                candidates.add(href);
            }
        }
        for (String c : candidates) {
            if (c.contains("/product/")) {
                return c;
            }
        }
        return candidates.isEmpty() ? null : candidates.iterator().next();
    }

    private String tryOpenProductBySkuPatterns(Page page, String base, String sku) {
        List<String> guesses = new ArrayList<>();
        if ("81976749".equals(sku)) {
            guesses.add("https://lemanapro.ru/product/laminat-dub-severnyy-33-klass-tolshchina-8-mm-2153-m-81976749/");
            guesses.add("https://kazan.lemanapro.ru/product/laminat-dub-severnyy-33-klass-tolshchina-8-mm-2153-m-81976749/");
        }
        for (String url : guesses) {
            try {
                log.info("Парсер LemanaPro: прямой URL — {}", url);
                navigateAndWaitQrator(page, url);
                if (!isHttp403(page) && page.url().contains("/product/")) {
                    return page.url();
                }
            } catch (Exception e) {
                log.debug("Прямой URL: {}", e.getMessage());
            }
        }
        return null;
    }

    private void ensureBrowser() {
        if (browser != null) {
            return;
        }
        boolean headless = appProperties.getLemana().isHeadless();
        String channel = appProperties.getLemana().getBrowserChannel();
        log.info("Парсер LemanaPro: запуск браузера headless={}, channel='{}'",
                headless, channel == null ? "" : channel);

        playwright = Playwright.create();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(java.util.List.of(
                        "--disable-blink-features=AutomationControlled",
                        "--no-sandbox",
                        "--disable-dev-shm-usage"
                ));
        if (channel != null && !channel.isBlank()) {
            opts.setChannel(channel);
        }
        try {
            browser = playwright.chromium().launch(opts);
            log.info("Парсер LemanaPro: браузер запущен");
        } catch (Exception e) {
            log.error("Парсер LemanaPro: не удалось запустить браузер: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void delay() {
        long ms = appProperties.getLemana().getRequestDelayMs();
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @PreDestroy
    public void shutdown() {
        lock.lock();
        try {
            if (browser != null) {
                browser.close();
                browser = null;
            }
            if (playwright != null) {
                playwright.close();
                playwright = null;
            }
            log.info("Парсер LemanaPro: браузер закрыт");
        } finally {
            lock.unlock();
        }
    }
}
