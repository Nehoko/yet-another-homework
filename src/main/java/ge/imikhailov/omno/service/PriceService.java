package ge.imikhailov.omno.service;

import ge.imikhailov.omno.dto.AdjustmentDto;
import ge.imikhailov.omno.dto.PriceDto;
import ge.imikhailov.omno.entity.PriceAdjustment;
import ge.imikhailov.omno.entity.Product;
import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.repoisotory.PriceAdjustmentRepository;
import ge.imikhailov.omno.repoisotory.ProductRepository;
import ge.imikhailov.omno.web.error.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceService {

    private final ProductRepository productRepository;
    private final PriceAdjustmentRepository priceAdjustmentRepository;
    private final AdjustmentMapper adjustmentMapper;
    private final MeterRegistry meterRegistry;

    @Cacheable(cacheNames = "price", key = "#productId", sync = true)
    @Transactional
    @Observed(
            name = "price.get",
            contextualName = "get price",
            lowCardinalityKeyValues = {"product.id", "#productId"}
    )
    public PriceDto getPrice(Long productId) {
        log.info("Getting price for product {}", productId);
        final Timer.Sample sampleFindById = Timer.start(meterRegistry);
        final Optional<Product> oProduct = productRepository.findById(productId);
        sampleFindById.stop(
                Timer.builder("omno.db.query")
                        .tag("entity", "Product")
                        .tag("op", "findById")
                        .publishPercentileHistogram()
                        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                        .register(meterRegistry)
        );
        if (oProduct.isEmpty()) {
            log.warn("Product not found for product id {}", productId);
            throw new ProductNotFoundException(productId);
        }
        final var product = oProduct.get();
        log.info("Found product {}", product.getName());
        final Timer.Sample sampleFindAdjust = Timer.start(meterRegistry);
        final List<AdjustmentDto> adjustmentDtoList = priceAdjustmentRepository.findByProduct(product).stream()
                .map(adjustmentMapper::toDto)
                .toList();
        sampleFindAdjust.stop(
                Timer.builder("omno.db.query")
                        .tag("entity", "PriceAdjustment")
                        .tag("op", "findByProduct")
                        .publishPercentileHistogram()
                        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                        .register(meterRegistry)
        );
        log.info("Found {} adjustments", adjustmentDtoList.size());
        return new PriceDto(product.getId(), product.getBasePrice(), calculateFinalPrice(product.getBasePrice(), adjustmentDtoList), adjustmentDtoList);
    }

    @CacheEvict(cacheNames = "price", key = "#productId")
    @Transactional
    @Observed(
            name = "price.setAdjustments",
            contextualName = "set adjustments",
            lowCardinalityKeyValues = {"product.id", "#productId", "adjustments.count", "#adjustmentDtoList?.size()"}
    )
    public void setAdjustments(Long productId, List<AdjustmentDto> adjustmentDtoList) {
        final Timer.Sample sampleFindById = Timer.start(meterRegistry);
        final Optional<Product> oProduct = productRepository.findById(productId);
        sampleFindById.stop(
                Timer.builder("omno.db.query")
                        .tag("entity", "Product")
                        .tag("op", "findById")
                        .publishPercentileHistogram()
                        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                        .register(meterRegistry)
        );
        if (oProduct.isEmpty()) {
            throw new ProductNotFoundException(productId);
        }
        final var product = oProduct.get();
        final List<PriceAdjustment> adjustments = adjustmentMapper.toEntity(adjustmentDtoList, product);
        adjustments.forEach(a -> a.setUpdatedAt(OffsetDateTime.now()));
        priceAdjustmentRepository.saveAll(adjustments);
    }

    private BigDecimal calculateFinalPrice(BigDecimal basePrice, List<AdjustmentDto> adjustmentDtoList) {
        return adjustmentDtoList.stream()
                .map(a -> calculateAdjustedPrice(a, basePrice))
                .reduce(basePrice, BigDecimal::add);
    }

    private BigDecimal calculateAdjustedPrice(AdjustmentDto adjustmentDto, BigDecimal basePrice) {
        if (adjustmentDto == null) {
            return BigDecimal.ZERO;
        }
        final BigDecimal value = adjustmentDto.value();
        final AdjustmentMode mode = adjustmentDto.mode();
        return switch (mode) {
            case PERCENT -> value.multiply(basePrice).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN);
            case ABSOLUTE -> value;
        };
    }
}
