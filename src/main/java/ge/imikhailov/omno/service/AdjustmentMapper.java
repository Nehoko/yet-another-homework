package ge.imikhailov.omno.service;

import ge.imikhailov.omno.dto.AdjustmentDto;
import ge.imikhailov.omno.entity.PriceAdjustment;
import ge.imikhailov.omno.entity.Product;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdjustmentMapper {

    public AdjustmentDto toDto(PriceAdjustment adjustment) {
        return new AdjustmentDto(adjustment.getValue(), adjustment.getType(), adjustment.getMode());
    }

    public PriceAdjustment toEntity(AdjustmentDto adjustmentDto, Product product) {
        final PriceAdjustment priceAdjustment = new PriceAdjustment();
        priceAdjustment.setValue(adjustmentDto.value());
        priceAdjustment.setType(adjustmentDto.type());
        priceAdjustment.setMode(adjustmentDto.mode());
        if (product != null) {
            priceAdjustment.setProduct(product);
        }
        return priceAdjustment;
    }

    public List<AdjustmentDto> toDto(List<PriceAdjustment> adjustments) {
        return adjustments.stream().map(this::toDto).toList();
    }

    public List<PriceAdjustment> toEntity(List<AdjustmentDto> adjustmentDtoList, Product product) {
        return adjustmentDtoList.stream().map(a -> toEntity(a, product)).toList();
    }
}
