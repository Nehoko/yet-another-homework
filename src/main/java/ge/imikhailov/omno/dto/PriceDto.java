package ge.imikhailov.omno.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceDto(
        Long productId,
        BigDecimal basePrice,
        BigDecimal finalPrice,
        List<AdjustmentDto> adjustments
) {
}
