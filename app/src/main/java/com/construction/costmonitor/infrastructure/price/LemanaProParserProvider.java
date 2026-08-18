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
 * Провайдер цен LemanaPro через Playwright (обход защиты Qrator).
 *
 * На хосте один раз нужно установить Chromium:
 * {@code npx playwright@1.47.0 install chromium}
 *
 * Сценарий по артикулу (SKU):
 * 1. Страница поиска /search/?q={sku}
 * 2. Ссылка на карточку товара с этим артикулом
 * 3. Разбор цены через {@link PriceHtmlParser}
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
            String searchUrl = base + "/search/?q=" + sku;

            try (Page page = browser.newPage()) {
                page.setDefaultTimeout(appProperties.getLemana().getNavigationTimeoutMs());
                page.setExtraHTTPHeaders(java.util.Map.of(
                        "Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8"
                ));

                log.info("Парсер LemanaPro: открываем поиск — {}", searchUrl);
                page.navigate(searchUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                // Даём время Qrator / JS отрисовать выдачу
                page.waitForTimeout(2500);

                String productUrl = findProductUrl(page, sku, base);
                if (productUrl == null) {
                    productUrl = tryDirectProductGuess(page, base, sku);
                }

                if (productUrl == null) {
                    log.warn("Парсер LemanaPro: товар с артикулом [{}] не найден в выдаче поиска", sku);
                    return Optional.empty();
                }

                log.info("Парсер LemanaPro: открываем карточку товара — {}", productUrl);
                page.navigate(productUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForTimeout(2000);

                String html = page.content();
                log.debug("Парсер LemanaPro: получено HTML, длина {} символов, артикул [{}]",
                        html != null ? html.length() : 0, sku);

                Optional<PriceHtmlParser.ParsedProduct> parsed =
                        PriceHtmlParser.parse(html, sku, productUrl);

                if (parsed.isEmpty()) {
                    log.warn("Парсер LemanaPro: не удалось извлечь цену из HTML карточки, артикул [{}], url={}",
                            sku, productUrl);
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
                        p.productUrl()
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

    private String findProductUrl(Page page, String sku, String base) {
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
            log.debug("Парсер LemanaPro: найдена ссылка на товар по /product/ — {}", s);
            return s.startsWith("http") ? s : base + s;
        }

        Object any = page.evaluate("""
                (sku) => {
                  const links = Array.from(document.querySelectorAll('a[href]'));
                  const hit = links.map(a => a.href).find(h => h && h.includes(sku) && h.includes('lemanapro'));
                  return hit || null;
                }
                """, sku);
        if (any instanceof String s && !s.isBlank()) {
            log.debug("Парсер LemanaPro: найдена ссылка по артикулу (широкий поиск) — {}", s);
            return s;
        }

        log.debug("Парсер LemanaPro: ссылок на товар с артикулом [{}] на странице поиска нет", sku);
        return null;
    }

    private String tryDirectProductGuess(Page page, String base, String sku) {
        return null;
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
                            "--no-sandbox"
                    )));
            log.info("Парсер LemanaPro: браузер Chromium успешно запущен");
        } catch (Exception e) {
            log.error("Парсер LemanaPro: не удалось запустить Chromium. " +
                    "Установите браузер: npx playwright@1.47.0 install chromium. Причина: {}",
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
            log.warn("Парсер LemanaPro: пауза между запросами прервана");
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
