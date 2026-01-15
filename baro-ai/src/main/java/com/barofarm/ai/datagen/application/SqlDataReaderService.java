package com.barofarm.ai.datagen.application;

import com.barofarm.ai.datagen.application.dto.ProductGenerationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 파일에서 상품 데이터를 읽어오는 서비스
 */
@Slf4j
@Service
public class SqlDataReaderService {

    private static final String[] VALID_CATEGORIES = {"FRUIT", "VEGETABLE", "GRAIN", "NUT", "ROOT", "MUSHROOM", "ETC"};

    /**
     * SQL 파일에서 상품 데이터를 파싱하여 ProductGenerationDto 리스트로 반환
     */
    public List<ProductGenerationDto> readProductsFromSql(String sqlFilePath) throws IOException {
        log.info("SQL 파일에서 상품 데이터 읽기 시작: {}", sqlFilePath);

        Path path = Paths.get(sqlFilePath);
        if (!Files.exists(path)) {
            // 클래스패스에서 찾기 시도
            try {
                ClassPathResource resource = new ClassPathResource(sqlFilePath);
                path = resource.getFile().toPath();
            } catch (Exception e) {
                throw new IOException("SQL 파일을 찾을 수 없습니다: " + sqlFilePath, e);
            }
        }

        String content = Files.readString(path);
        List<ProductGenerationDto> products = parseSqlContent(content);

        log.info("SQL 파일 파싱 완료: {}개 상품 데이터 추출", products.size());
        return products;
    }

    private List<ProductGenerationDto> parseSqlContent(String content) {
        List<ProductGenerationDto> products = new ArrayList<>();

        // INSERT 문 패턴 매칭 (Python 스크립트의 정규식과 동일)
        String regex = "INSERT INTO `product`.*?VALUES\\s*\\(uuid_to_bin\\('([^']+)'\\),\\s*"
            + "uuid_to_bin\\('([^']+)'\\),\\s*'([^']+)',\\s*'([^']+)',\\s*'([^']+)',\\s*(\\d+),\\s*"
            + "'([^']+)',\\s*'([^']+)',\\s*'([^']+)'\\);";
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);

        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String productName = matcher.group(3);
            String description = matcher.group(4);
            String category = matcher.group(5);
            int price = Integer.parseInt(matcher.group(6));

            // 유효한 카테고리만 포함
            if (isValidCategory(category)) {
                products.add(new ProductGenerationDto(productName, description, category, price, 100, "ON_SALE"));
            }
        }

        return products;
    }

    private boolean isValidCategory(String category) {
        for (String validCategory : VALID_CATEGORIES) {
            if (validCategory.equals(category)) {
                return true;
            }
        }
        return false;
    }
}
