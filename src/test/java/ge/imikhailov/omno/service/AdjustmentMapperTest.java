package ge.imikhailov.omno.service;

import ge.imikhailov.omno.dto.AdjustmentDto;
import ge.imikhailov.omno.entity.PriceAdjustment;
import ge.imikhailov.omno.entity.Product;
import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.enums.AdjustmentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdjustmentMapperTest {

    private final AdjustmentMapper mapper = new AdjustmentMapper();

    @Test
    void mapsEntityToDtoAndBack() {
        Product product = new Product();
        product.setId(42L);
        PriceAdjustment adj = new PriceAdjustment();
        adj.setProduct(product);
        adj.setMode(AdjustmentMode.PERCENT);
        adj.setType(AdjustmentType.PROMO);
        adj.setValue(BigDecimal.valueOf(5.25));

        AdjustmentDto dto = mapper.toDto(adj);
        assertThat(dto.value()).isEqualByComparingTo("5.25");
        assertThat(dto.type()).isEqualTo(AdjustmentType.PROMO);
        assertThat(dto.mode()).isEqualTo(AdjustmentMode.PERCENT);

        PriceAdjustment roundTrip = mapper.toEntity(dto, product);
        assertThat(roundTrip.getProduct()).isSameAs(product);
        assertThat(roundTrip.getMode()).isEqualTo(AdjustmentMode.PERCENT);
        assertThat(roundTrip.getType()).isEqualTo(AdjustmentType.PROMO);
        assertThat(roundTrip.getValue()).isEqualByComparingTo("5.25");
    }

    @Test
    void mapsListsBothDirections() {
        Product product = new Product();
        product.setId(7L);
        AdjustmentDto dto1 = new AdjustmentDto(BigDecimal.ONE, AdjustmentType.FEE, AdjustmentMode.ABSOLUTE);
        AdjustmentDto dto2 = new AdjustmentDto(BigDecimal.TEN, AdjustmentType.TAX, AdjustmentMode.PERCENT);

        List<PriceAdjustment> entities = mapper.toEntity(List.of(dto1, dto2), product);
        assertThat(entities).hasSize(2);
        assertThat(entities).allMatch(e -> e.getProduct() == product);

        List<AdjustmentDto> backToDto = mapper.toDto(entities);
        assertThat(backToDto).containsExactlyElementsOf(List.of(dto1, dto2));
    }

    @Test
    void allowsNullProductWhenMappingEntity() {
        AdjustmentDto dto = new AdjustmentDto(BigDecimal.valueOf(3.14), AdjustmentType.TAX, AdjustmentMode.ABSOLUTE);

        PriceAdjustment entity = mapper.toEntity(dto, null);

        assertThat(entity.getProduct()).isNull();
        assertThat(entity.getValue()).isEqualByComparingTo("3.14");
        assertThat(entity.getType()).isEqualTo(AdjustmentType.TAX);
        assertThat(entity.getMode()).isEqualTo(AdjustmentMode.ABSOLUTE);
    }
}
