package ge.imikhailov.omno.it;

import ge.imikhailov.omno.dto.AdjustmentDto;
import ge.imikhailov.omno.dto.PriceDto;
import ge.imikhailov.omno.entity.Product;
import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.enums.AdjustmentType;
import ge.imikhailov.omno.repoisotory.PriceAdjustmentRepository;
import ge.imikhailov.omno.repoisotory.ProductRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class PriceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    PriceAdjustmentRepository priceAdjustmentRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        priceAdjustmentRepository.deleteAll();
        productRepository.deleteAll();

        Product p = new Product();
        p.setSku("sku-it-1");
        p.setName("IT Product");
        p.setBasePrice(new BigDecimal("100.00"));
        p = productRepository.save(p);
        productId = p.getId();
    }

    @AfterAll
    static void tearDown() {
        redis.close();
    }

    @Test
    void getPrice_returnsComputedDto_and_404_for_missing() {
        // Seed two adjustments via admin API
        List<AdjustmentDto> list = List.of(
                new AdjustmentDto(new BigDecimal("10.00"), AdjustmentType.PROMO, AdjustmentMode.ABSOLUTE),
                new AdjustmentDto(new BigDecimal("5.0"), AdjustmentType.TAX, AdjustmentMode.PERCENT)
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<AdjustmentDto>> req = new HttpEntity<>(list, headers);
        ResponseEntity<Void> post = rest.postForEntity("/admin/price/{id}/adjustments", req, Void.class, productId);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();

        // GET price should reflect base + abs + percent(base)
        ResponseEntity<PriceDto> ok = rest.getForEntity("/price/{id}", PriceDto.class, productId);
        assertThat(ok.getStatusCode().is2xxSuccessful()).isTrue();
        PriceDto dto = ok.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.basePrice()).isEqualByComparingTo("100.00");
        assertThat(dto.finalPrice()).isEqualByComparingTo("115.00");
        assertThat(dto.adjustments()).hasSize(2);

        // 404 for missing product
        ResponseEntity<String> nf = rest.getForEntity("/price/{id}", String.class, 999999L);
        assertThat(nf.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void cacheWarm_andInvalidationOnAdjustments() {
        // Warm cache
        ResponseEntity<PriceDto> before = rest.getForEntity("/price/{id}", PriceDto.class, productId);
        assertThat(before.getStatusCode().is2xxSuccessful()).isTrue();
        BigDecimal initialFinal = before.getBody().finalPrice();

        // Change adjustments via admin API to alter final price by +20
        List<AdjustmentDto> list = List.of(
                new AdjustmentDto(new BigDecimal("20.00"), AdjustmentType.PROMO, AdjustmentMode.ABSOLUTE)
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<AdjustmentDto>> req = new HttpEntity<>(list, headers);
        ResponseEntity<Void> post = rest.postForEntity("/admin/price/{id}/adjustments", req, Void.class, productId);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();

        // Subsequent GET should reflect new final price
        ResponseEntity<PriceDto> after = rest.getForEntity("/price/{id}", PriceDto.class, productId);
        assertThat(after.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(after.getBody().finalPrice()).isEqualByComparingTo(initialFinal.add(new BigDecimal("20.00")));
    }
}
