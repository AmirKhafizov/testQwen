package com.construction.costmonitor.infrastructure.price;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PriceHtmlParserTest {

    @Test
    void parseDecimalHandlesRuFormat() {
        assertEquals(new BigDecimal("1299.50"), PriceHtmlParser.parseDecimal("1 299,50 ₽").orElseThrow());
        assertEquals(new BigDecimal("598"), PriceHtmlParser.parseDecimal("598").orElseThrow());
    }

    @Test
    void extractsPriceFromMetaItemprop() {
        String html = """
                <html><body>
                <h1>Цемент М500 50 кг</h1>
                <meta itemprop="price" content="459.00"/>
                </body></html>
                """;

        Optional<PriceHtmlParser.ParsedProduct> p =
                PriceHtmlParser.parse(html, "12345", "https://kazan.lemanapro.ru/product/x-12345/");

        assertTrue(p.isPresent());
        assertEquals(new BigDecimal("459.00"), p.get().price());
        assertEquals("Цемент М500 50 кг", p.get().productName());
    }

    @Test
    void extractsPriceFromEmbeddedJson() {
        String html = """
                <html><body><script>
                window.__STATE__ = {"price": "1890.5", "name": "Штукатурка"};
                </script></body></html>
                """;

        Optional<PriceHtmlParser.ParsedProduct> p =
                PriceHtmlParser.parse(html, "999", "http://example");

        assertTrue(p.isPresent());
        assertEquals(0, new BigDecimal("1890.5").compareTo(p.get().price()));
    }

    @Test
    void returnsEmptyWhenNoPrice() {
        String html = "<html><body><h1>Товар</h1></body></html>";
        assertTrue(PriceHtmlParser.parse(html, "1", "u").isEmpty());
    }
}
