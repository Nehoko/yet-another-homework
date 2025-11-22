package ge.imikhailov.omno.service;

import ge.imikhailov.omno.dto.AdjustmentDto;
import ge.imikhailov.omno.dto.PriceDto;
import ge.imikhailov.omno.entity.Product;
import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.repoisotory.PriceAdjustmentRepository;
import ge.imikhailov.omno.repoisotory.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriceService {

    private final ProductRepository productRepository;
    private final PriceAdjustmentRepository priceAdjustmentRepository;
    private final AdjustmentMapper adjustmentMapper;

    @Transactional
    public PriceDto getPrice(Long productId) {
        final Optional<Product> oProduct = productRepository.findById(productId);
        if (oProduct.isEmpty()) {
            return null;
        }
        final var product = oProduct.get();

        final List<AdjustmentDto> adjustmentDtoList = priceAdjustmentRepository.findByProduct(product).stream()
                .map(adjustmentMapper::toDto)
                .toList();
        return new PriceDto(product.getId(), product.getBasePrice(), calculateFinalPrice(product.getBasePrice(), adjustmentDtoList), adjustmentDtoList);
    }

    @Transactional
    public void setAdjustments(Long productId, List<AdjustmentDto> adjustmentDtoList) {
        final Optional<Product> oProduct = productRepository.findById(productId);
        if (oProduct.isEmpty()) {
            throw new IllegalArgumentException("Product not found");
        }
        final var product = oProduct.get();
        priceAdjustmentRepository.saveAll(adjustmentMapper.toEntity(adjustmentDtoList, product));
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
