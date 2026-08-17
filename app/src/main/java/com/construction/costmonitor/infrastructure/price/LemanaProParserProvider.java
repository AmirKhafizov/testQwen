package com.construction.costmonitor.infrastructure.price;

import com.construction.costmonitor.config.AppProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LemanaPro price provider using Playwright to bypass Qrator anti-bot.
 *
 * Prerequisites on the host:
 *   mvn/gradle dependency pulls playwright jars;
 *   once: {@code mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"}
 *   or: {@code ./gradlew :app:dependencies} then run {@code npx playwright install chromium} if needed.
 *
 * Flow for SKU:
 * 1. Open search page {@code /search/?q={sku}}
 * 2. Find first product link containing the SKU
 * 3. Open product page and extract price via {@link PriceHtmlParser}
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
            return Optional.empty();
        }

        lock.lock();
        try {
            ensureBrowser();
            delay();

            String base = trimSlash(appProperties.getLemana().getBaseUrl());
            String searchUrl = base + "/search/?q=" + externalSku.trim();

            try (Page page = browser.newPage()) {
                page.setDefaultTimeout(appProperties.getLemana().getNavigationTimeoutMs());
                page.setExtraHTTPHeaders(java.util.Map.of(
                        "Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8"
                ));

                log.info("LemanaPro: opening search {}", searchUrl);
                page.navigate(searchUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                // Give Qrator / JS a moment
                page.waitForTimeout(2500);

                String productUrl = findProductUrl(page, externalSku, base);
                if (productUrl == null) {
                    // Fallback: sometimes product is reachable by article in path
                    productUrl = tryDirectProductGuess(page, base, externalSku);
                }

                if (productUrl == null) {
                    log.warn("LemanaPro: product not found for sku={}", externalSku);
                    return Optional.empty();
                }

                log.info("LemanaPro: opening product {}", productUrl);
                page.navigate(productUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForTimeout(2000);

                String html = page.content();
                Optional<PriceHtmlParser.ParsedProduct> parsed =
                        PriceHtmlParser.parse(html, externalSku, productUrl);

                return parsed.map(p -> new PriceQuote(
                        p.externalSku(),
                        p.productName(),
                        p.price(),
                        p.currency(),
                        p.productUrl()
                ));
            }
        } catch (Exception e) {
            log.error("LemanaPro: failed to fetch price for sku={}", externalSku, e);
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    private String findProductUrl(Page page, String sku, String base) {
        // Collect hrefs that look like product pages and contain the article number
        Object result = page.evaluate("""
                (sku) => {
                  const links = Array.from(document.querySelectorAll('a[href]'));
                  const candidates = links
                    .map(a => a.href)
                    .filter(h => h && (h.includes('/product/') || h.includes('/p/')))
                    .filter(h => h.includes(sku));
                  return candidates.length ? candidates[0] : null;
                }
                """, sku);

        if (result instanceof String s && !s.isBlank()) {
            return s.startsWith("http") ? s : base + s;
        }

        // Broader: any link with sku in href
        Object any = page.evaluate("""
                (sku) => {
                  const links = Array.from(document.querySelectorAll('a[href]'));
                  const hit = links.map(a => a.href).find(h => h && h.includes(sku) && h.includes('lemanapro'));
                  return hit || null;
                }
                """, sku);
        if (any instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private String tryDirectProductGuess(Page page, String base, String sku) {
        // Some regions expose /product/{slug}-{sku}/
        // We only return URL if navigation succeeds and price parses later.
        return null;
    }

    private void ensureBrowser() {
        if (browser != null) {
            return;
        }
        log.info("Starting Playwright Chromium (headless={})", appProperties.getLemana().isHeadless());
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(appProperties.getLemana().isHeadless())
                .setArgs(java.util.List.of(
                        "--disable-blink-features=AutomationControlled",
                        "--no-sandbox"
                )));
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
        } finally {
            lock.unlock();
        }
    }
}
