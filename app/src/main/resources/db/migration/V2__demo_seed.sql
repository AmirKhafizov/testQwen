-- Демо-данные для локальной разработки (mock-провайдер цен)
INSERT INTO companies (code, name, active)
VALUES ('demo', 'Демо Строй', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO materials (company_id, code, name, unit_of_measure, category, active)
SELECT c.id, 'LAM-SEV', 'Ламинат Дуб Северный', 'м2', 'Напольные покрытия', true
FROM companies c WHERE c.code = 'demo'
ON CONFLICT ON CONSTRAINT uk_materials_company_code DO NOTHING;

INSERT INTO materials (company_id, code, name, unit_of_measure, category, active)
SELECT c.id, 'LAM-ULV', 'Ламинат Дуб ульвар', 'м2', 'Напольные покрытия', true
FROM companies c WHERE c.code = 'demo'
ON CONFLICT ON CONSTRAINT uk_materials_company_code DO NOTHING;

INSERT INTO external_products (source, external_sku, name, unit_of_measure)
VALUES
    ('LEMANA_PRO', '81976749', 'Ламинат Дуб Северный 33 класс 8 мм', 'м2'),
    ('LEMANA_PRO', '85999876', 'Ламинат Дуб ульвар 33 класс 8 мм', 'м2')
ON CONFLICT ON CONSTRAINT uk_external_products_source_sku DO NOTHING;

INSERT INTO material_mappings (company_id, material_id, external_product_id, status, matched_by, confidence)
SELECT m.company_id, m.id, ep.id, 'CONFIRMED', 'MANUAL', 1.0000
FROM materials m
JOIN companies c ON c.id = m.company_id AND c.code = 'demo'
JOIN external_products ep ON ep.source = 'LEMANA_PRO' AND ep.external_sku = '81976749'
WHERE m.code = 'LAM-SEV'
ON CONFLICT ON CONSTRAINT uk_material_mappings_material DO NOTHING;

INSERT INTO material_mappings (company_id, material_id, external_product_id, status, matched_by, confidence)
SELECT m.company_id, m.id, ep.id, 'CONFIRMED', 'MANUAL', 1.0000
FROM materials m
JOIN companies c ON c.id = m.company_id AND c.code = 'demo'
JOIN external_products ep ON ep.source = 'LEMANA_PRO' AND ep.external_sku = '85999876'
WHERE m.code = 'LAM-ULV'
ON CONFLICT ON CONSTRAINT uk_material_mappings_material DO NOTHING;
