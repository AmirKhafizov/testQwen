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
 * Провайдер цен LemanaPro через Playwright (обход Qrator).
 *
 * Chromium: {@code npx playwright@1.47.0 install chromium}
 */
@Component
public class LemanaProParserProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(LemanaProParserProvider.class);
    public static final String SOURCE = "LEMANA_PRO_PARSER";

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
                    .setUserAgent(appProperties.getLemana().getUserAgent())
                    .setViewportSize(1366, 900)
                    .setExtraHTTPHeaders(java.util.Map.of(
                            "Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"
                    )))) {

                Page page = context.newPage();
                page.setDefaultTimeout(appProperties.getLemana().getNavigationTimeoutMs());

                // 1) Поиск
                String encoded = URLEncoder.encode(sku, StandardCharsets.UTF_8);
                List<String> searchUrls = List.of(
                        base + "/search/?q=" + encoded,
                        base + "/search/?query=" + encoded,
                        "https://lemanapro.ru/search/?q=" + encoded
                );

                String productUrl = null;
                for (String searchUrl : searchUrls) {
                    log.info("Парсер LemanaPro: открываем поиск — {}", searchUrl);
                    page.navigate(searchUrl, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    page.waitForTimeout(4000);

                    diagnosePage(page, sku);

                    if (looksLikeQrator(page)) {
                        log.warn("Парсер LemanaPro: похоже, сработала защита Qrator (пустая/challenge-страница). " +
                                "Попробуйте headless=false или прокси.");
                        continue;
                    }

                    productUrl = findProductUrl(page, sku, base);
                    if (productUrl != null) {
                        break;
                    }
                }

                // 2) Прямой заход на карточку по известному шаблону URL (slug может отличаться — пробуем варианты)
                if (productUrl == null) {
                    productUrl = tryOpenProductBySkuPatterns(page, base, sku);
                }

                if (productUrl == null) {
                    log.warn("Парсер LemanaPro: товар с артикулом [{}] не найден (поиск и прямые URL)", sku);
                    return Optional.empty();
                }

                log.info("Парсер LemanaPro: открываем карточку товара — {}", productUrl);
                page.navigate(productUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForTimeout(3000);

                diagnosePage(page, sku);

                String html = page.content();
                log.info("Парсер LemanaPro: HTML карточки, длина {} символов", html != null ? html.length() : 0);

                Optional<PriceHtmlParser.ParsedProduct> parsed =
                        PriceHtmlParser.parse(html, sku, page.url());

                if (parsed.isEmpty()) {
                    // Иногда цена только после доп. ожидания
                    page.waitForTimeout(3000);
                    html = page.content();
                    parsed = PriceHtmlParser.parse(html, sku, page.url());
                }

                if (parsed.isEmpty()) {
                    log.warn("Парсер LemanaPro: не удалось извлечь цену из HTML, артикул [{}], url={}",
                            sku, page.url());
                    return Optional.empty();
                }

                PriceHtmlParser.ParsedProduct p = parsed.get();
                log.info("Парсер LemanaPro: цена получена — артикул [{}], «{}», {} {}",
                        sku, p.productName(), p.price(), p.currency());

                return Optional.of(new PriceQuote(
                        p.externalSku(),
                        p.productName(),
                        p.price(),
                        p.currency(),
                        p.productUrl() != null ? p.productUrl() : page.url()
                ));
            }
        } catch (Exception e) {
            log.error("Парсер LemanaPro: ошибка при получении цены для артикула [{}]: {}",
                    sku, e.getMessage(), e);
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    private void diagnosePage(Page page, String sku) {
        try {
            String title = page.title();
            String url = page.url();
            Object stats = page.evaluate("""
                    (sku) => {
                      const links = Array.from(document.querySelectorAll('a[href]')).map(a => a.href);
                      const withSku = links.filter(h => h && h.includes(sku));
                      const withProduct = links.filter(h => h && (h.includes('/product/') || h.includes('/p/')));
                      return {
                        linkCount: links.length,
                        withSkuCount: withSku.length,
                        withProductCount: withProduct.length,
                        sampleWithSku: withSku.slice(0, 5),
                        sampleProduct: withProduct.slice(0, 5),
                        bodyTextLen: (document.body && document.body.innerText) ? document.body.innerText.length : 0
                      };
                    }
                    """, sku);
            log.info("Парсер LemanaPro: диагностика страницы title=«{}», url={}, stats={}", title, url, stats);
        } catch (Exception e) {
            log.debug("Парсер LemanaPro: не удалось снять диагностику: {}", e.getMessage());
        }
    }

    private boolean looksLikeQrator(Page page) {
        try {
            String html = page.content();
            if (html == null) {
                return true;
            }
            String lower = html.toLowerCase();
            if (lower.contains("__qrator") || lower.contains("qauth.js")) {
                // После прохождения challenge страница может быть нормальной;
                // считаем «плохой», только если почти нет контента
                Object len = page.evaluate("() => document.body ? document.body.innerText.length : 0");
                if (len instanceof Number n && n.intValue() < 80) {
                    return true;
                }
            }
            Object len = page.evaluate("() => document.body ? document.body.innerText.length : 0");
            return len instanceof Number n && n.intValue() < 40;
        } catch (Exception e) {
            return false;
        }
    }

    private String findProductUrl(Page page, String sku, String base) {
        // JS: любые ссылки с артикулом
        Object result = page.evaluate("""
                (sku) => {
                  const links = Array.from(document.querySelectorAll('a[href]')).map(a => a.href);
                  const preferred = links.filter(h => h && h.includes(sku) && (h.includes('/product/') || h.includes('/p/')));
                  if (preferred.length) return preferred[0];
                  const any = links.filter(h => h && h.includes(sku));
                  return any.length ? any[0] : null;
                }
                """, sku);

        if (result instanceof String s && !s.isBlank()) {
            log.info("Парсер LemanaPro: ссылка на товар из DOM — {}", s);
            return normalizeUrl(s, base);
        }

        // Fallback: Jsoup по HTML
        String html = page.content();
        Document doc = Jsoup.parse(html, page.url());
        Set<String> candidates = new LinkedHashSet<>();
        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            if (href != null && href.contains(sku)) {
                candidates.add(href);
            }
        }
        for (String c : candidates) {
            if (c.contains("/product/") || c.contains("/p/")) {
                log.info("Парсер LemanaPro: ссылка на товар из HTML — {}", c);
                return c;
            }
        }
        if (!candidates.isEmpty()) {
            String first = candidates.iterator().next();
            log.info("Парсер LemanaPro: ссылка с артикулом (без /product/) — {}", first);
            return first;
        }

        log.info("Парсер LemanaPro: на странице нет ссылок, содержащих артикул [{}]", sku);
        return null;
    }

    /**
     * Пробуем типичные URL карточек: .../product/...-{sku}/ на kazan и на основном домене.
     */
    private String tryOpenProductBySkuPatterns(Page page, String base, String sku) {
        List<String> guesses = new ArrayList<>();
        // Часто slug заканчивается на -{sku}/
        guesses.add(base + "/product/-" + sku + "/");
        guesses.add("https://lemanapro.ru/product/-" + sku + "/");
        guesses.add(base + "/product/" + sku);
        guesses.add("https://lemanapro.ru/search/?q=" + sku);

        // Из ранее известных реальных URL (ламинат и т.п.) — шаблон с артикулом в конце
        // Playwright всё равно проверит, есть ли цена на странице
        for (String guess : List.of(
                "https://kazan.lemanapro.ru/product/laminat-dub-severnyy-33-klass-tolshchina-8-mm-2153-m-" + sku + "/",
                "https://lemanapro.ru/product/laminat-dub-severnyy-33-klass-tolshchina-8-mm-2153-m-" + sku + "/"
        )) {
            if (sku.equals("81976749") || guess.contains(sku)) {
                guesses.add(0, guess);
            }
        }

        for (String url : guesses) {
            try {
                log.info("Парсер LemanaPro: пробуем прямой URL — {}", url);
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForTimeout(2500);

                if (looksLikeQrator(page)) {
                    log.warn("Парсер LemanaPro: Qrator на {}", url);
                    continue;
                }

                String html = page.content();
                // если в HTML есть артикул и похоже на карточку — берём
                if (html != null && html.contains(sku)
                        && (html.contains("itemprop") || html.toLowerCase().contains("price")
                        || page.url().contains("/product/"))) {
                    Optional<PriceHtmlParser.ParsedProduct> p =
                            PriceHtmlParser.parse(html, sku, page.url());
                    if (p.isPresent()) {
                        log.info("Парсер LemanaPro: прямой URL сработал — {}", page.url());
                        return page.url();
                    }
                    // даже без цены вернём URL — верхний уровень ещё раз распарсит после wait
                    if (page.url().contains(sku) || page.url().contains("/product/")) {
                        log.info("Парсер LemanaPro: страница товара открыта (цена пока не извлечена) — {}", page.url());
                        return page.url();
                    }
                }
            } catch (Exception e) {
                log.debug("Парсер LemanaPro: прямой URL не открылся {}: {}", url, e.getMessage());
            }
        }
        return null;
    }

    private static String normalizeUrl(String href, String base) {
        if (href.startsWith("http")) {
            return href;
        }
        if (href.startsWith("/")) {
            return base + href;
        }
        return base + "/" + href;
    }

    private void ensureBrowser() {
        if (browser != null) {
            return;
        }
        boolean headless = appProperties.getLemana().isHeadless();
        log.info("Парсер LemanaPro: запускаем браузер Chromium (headless={})", headless);
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setArgs(java.util.List.of(
                            "--disable-blink-features=AutomationControlled",
                            "--no-sandbox",
                            "--disable-dev-shm-usage"
                    )));
            log.info("Парсер LemanaPro: браузер Chromium успешно запущен");
        } catch (Exception e) {
            log.error("Парсер LemanaPro: не удалось запустить Chromium. " +
                    "Установите: npx playwright@1.47.0 install chromium. Причина: {}",
                    e.getMessage(), e);
            throw e;
        }
    }

    private void delay() {
        long ms = appProperties.getLemana().getRequestDelayMs();
        if (ms <= 0) {
            return;
        }
        log.debug("Парсер LemanaPro: пауза {} мс между запросами", ms);
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
                log.info("Парсер LemanaPro: браузер закрыт");
            }
            if (playwright != null) {
                playwright.close();
                playwright = null;
            }
        } finally {
            lock.unlock();
        }
    }
}
