package com.ecommerce.product.service;

import com.ecommerce.category.entity.Category;
import com.ecommerce.category.repository.CategoryRepository;
import com.ecommerce.product.dto.ProductImportError;
import com.ecommerce.product.dto.ProductImportResponse;
import com.ecommerce.product.dto.ProductRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final ProductService productService;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ProductImportResponse importProducts(MultipartFile file) {
        List<ProductImportError> errors = new ArrayList<>();
        int totalRows = 0;
        int created = 0;

        try {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build();

            Iterable<CSVRecord> records = format.parse(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
            );

            for (CSVRecord record : records) {
                totalRows++;
                int rowNumber = (int) record.getRecordNumber() + 1;

                try {
                    ProductImportRow row = parseRow(record);
                    ProductRequest request = toProductRequest(row);

                    productService.createWithInitialStock(request, row.stockQty());
                    created++;

                } catch (Exception e) {
                    errors.add(new ProductImportError(rowNumber, e.getMessage()));
                }
            }

        } catch (Exception e) {
            errors.add(new ProductImportError(0, "Failed to read CSV file: " + e.getMessage()));
        }

        return new ProductImportResponse(
                totalRows,
                created,
                errors.size(),
                errors
        );
    }

    private ProductImportRow parseRow(CSVRecord record) {
        String name = required(record, "name");
        String brand = required(record, "brand");
        String description = optional(record, "description");
        BigDecimal price = parsePrice(required(record, "price"));
        String categorySlug = required(record, "categorySlug");
        int stockQty = parseStock(required(record, "stockQty"));

        String ram = optional(record, "ram_gb");
        String storage = optional(record, "storage_gb");
        String color = optional(record, "color");
        String imageUrlsRaw = optional(record, "imageUrls");

        return new ProductImportRow(
                name,
                brand,
                description,
                price,
                categorySlug,
                stockQty,
                ram,
                storage,
                color,
                imageUrlsRaw
        );
    }

    private ProductRequest toProductRequest(ProductImportRow row) {
        Category category = categoryRepository.findBySlug(row.categorySlug())
                .filter(Category::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active category slug not found: " + row.categorySlug()
                ));

        Map<String, Object> specs = new HashMap<>();

        if (row.ramGb() != null && !row.ramGb().isBlank()) {
            specs.put("ram_gb", Integer.parseInt(row.ramGb()));
        }

        if (row.storageGb() != null && !row.storageGb().isBlank()) {
            specs.put("storage_gb", Integer.parseInt(row.storageGb()));
        }

        if (row.color() != null && !row.color().isBlank()) {
            specs.put("color", row.color());
        }

        List<String> imageUrls = parseImageUrls(row.imageUrlsRaw());

        return new ProductRequest(
                row.name(),
                row.brand(),
                row.description(),
                row.price(),
                category.getId(),
                specs,
                imageUrls
        );
    }

    private String required(CSVRecord record, String column) {
        String value = optional(record, column);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required column value: " + column);
        }

        return value;
    }

    private String optional(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }

        String value = record.get(column);
        return value != null ? value.trim() : null;
    }

    private BigDecimal parsePrice(String value) {
        try {
            BigDecimal price = new BigDecimal(value);

            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Price cannot be negative");
            }

            return price;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid price: " + value);
        }
    }

    private int parseStock(String value) {
        try {
            int stockQty = Integer.parseInt(value);

            if (stockQty < 0) {
                throw new IllegalArgumentException("Stock quantity cannot be negative");
            }

            return stockQty;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid stock quantity: " + value);
        }
    }

    private List<String> parseImageUrls(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }

        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .toList();
    }

    private record ProductImportRow(
            String name,
            String brand,
            String description,
            BigDecimal price,
            String categorySlug,
            int stockQty,
            String ramGb,
            String storageGb,
            String color,
            String imageUrlsRaw
    ) {}
}