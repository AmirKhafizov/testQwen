package com.construction.costmonitor.infrastructure.price;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Извлечение названия и цены из HTML карточки LemanaPro.
 * Вынесено отдельно, чтобы unit-тесты работали без Playwright.
 */
public final class PriceHtmlParser {

    private static final Logger log = LoggerFactory.getLogger(PriceHtmlParser.class);

    private static final Pattern PRICE_NUMBER = Pattern.compile(
            "(?:\"price\"\\s*:\\s*|\"mainPrice\"\\s*:\\s*|\"value\"\\s*:\\s*)\"?([0-9]+(?:[.,][0-9]+)?)\"?");

    private static final Pattern META_PRICE = Pattern.compile(
            "itemprop=[\"']price[\"'][^>]*content=[\"']([0-9]+(?:[.,][0-9]+)?)[\"']" +
                    "|content=[\"']([0-9]+(?:[.,][0-9]+)?)[\"'][^>]*itemprop=[\"']price[\"']",
            Pattern.CASE_INSENSITIVE);

    private PriceHtmlParser() {
    }

    public static Optional<ParsedProduct> parse(String html, String externalSku, String pageUrl) {
        if (html == null || html.isBlank()) {
            log.warn("Разбор HTML: пустая страница для артикула [{}]", externalSku);
            return Optional.empty();
        }

        Document doc = Jsoup.parse(html);

        String name = extractName(doc);
        Optional<BigDecimal> price = extractPrice(doc, html);

        if (price.isEmpty()) {
            log.warn("Разбор HTML: цена не найдена для артикула [{}] (url={})", externalSku, pageUrl);
            return Optional.empty();
        }

        log.debug("Разбор HTML: артикул [{}], название «{}», цена {}",
                externalSku, name, price.get());

        return Optional.of(new ParsedProduct(
                externalSku,
                name != null ? name : "Артикул " + externalSku,
                price.get(),
                "RUB",
                pageUrl
        ));
    }

    private static String extractName(Document doc) {
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().trim();
        }
        Element og = doc.selectFirst("meta[property=og:title]");
        if (og != null) {
            return og.attr("content").trim();
        }
        Element title = doc.selectFirst("title");
        return title != null ? title.text().trim() : null;
    }

    private static Optional<BigDecimal> extractPrice(Document doc, String rawHtml) {
        Element metaPrice = doc.selectFirst("meta[itemprop=price]");
        if (metaPrice != null) {
            Optional<BigDecimal> p = parseDecimal(metaPrice.attr("content"));
            if (p.isPresent()) {
                log.debug("Разбор HTML: цена из meta[itemprop=price]");
                return p;
            }
        }

        String[] cssSelectors = {
                "[data-qa='product-price']",
                "[data-testid='product-price']",
                ".product-price",
                ".product-price__main",
                ".price-main",
                "[itemprop=price]",
                ".pdp-price",
                ".product__price"
        };
        for (String sel : cssSelectors) {
            Elements els = doc.select(sel);
            for (Element el : els) {
                Optional<BigDecimal> p = parseDecimal(el.attr("content"));
                if (p.isPresent()) {
                    log.debug("Разбор HTML: цена из селектора {} (content)", sel);
                    return p;
                }
                p = parseDecimal(el.attr("data-price"));
                if (p.isPresent()) {
                    log.debug("Разбор HTML: цена из селектора {} (data-price)", sel);
                    return p;
                }
                p = parseDecimal(el.text());
                if (p.isPresent()) {
                    log.debug("Разбор HTML: цена из селектора {} (текст)", sel);
                    return p;
                }
            }
        }

        Matcher m = PRICE_NUMBER.matcher(rawHtml);
        if (m.find()) {
            Optional<BigDecimal> p = parseDecimal(m.group(1));
            if (p.isPresent()) {
                log.debug("Разбор HTML: цена из встроенного JSON (price/mainPrice/value)");
                return p;
            }
        }

        Matcher meta = META_PRICE.matcher(rawHtml);
        if (meta.find()) {
            String g = meta.group(1) != null ? meta.group(1) : meta.group(2);
            Optional<BigDecimal> p = parseDecimal(g);
            if (p.isPresent()) {
                log.debug("Разбор HTML: цена из regex itemprop=price");
            }
            return p;
        }

        return Optional.empty();
    }

    static Optional<BigDecimal> parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String cleaned = raw
                .replace('\u00A0', ' ')
                .replace(" ", "")
                .replace(",", ".")
                .replaceAll("[^0-9.]", "");
        if (cleaned.isBlank() || cleaned.equals(".")) {
            return Optional.empty();
        }
        int firstDot = cleaned.indexOf('.');
        if (firstDot >= 0) {
            String head = cleaned.substring(0, firstDot + 1);
            String tail = cleaned.substring(firstDot + 1).replace(".", "");
            cleaned = head + tail;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public record ParsedProduct(
            String externalSku,
            String productName,
            BigDecimal price,
            String currency,
            String productUrl
    ) {}
}
