package ge.imikhailov.omno.service;

import ge.imikhailov.omno.dto.PriceDto;
import ge.imikhailov.omno.entity.PriceAdjustment;
import ge.imikhailov.omno.entity.Product;
import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.enums.AdjustmentType;
import ge.imikhailov.omno.repoisotory.PriceAdjustmentRepository;
import ge.imikhailov.omno.repoisotory.ProductRepository;
import ge.imikhailov.omno.web.error.ProductNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


class PriceServiceTest {

    private ProductRepository productRepository;
    private PriceAdjustmentRepository priceAdjustmentRepository;
    private AdjustmentMapper adjustmentMapper;
    private PriceService priceService;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        priceAdjustmentRepository = Mockito.mock(PriceAdjustmentRepository.class);
        adjustmentMapper = new AdjustmentMapper();
        priceService = new PriceService(productRepository, priceAdjustmentRepository, adjustmentMapper, new SimpleMeterRegistry());
    }

    private static Product product(long id, BigDecimal base) {
        Product p = new Product();
        p.setId(id);
        p.setName("p-" + id);
        p.setSku("sku-" + id);
        p.setBasePrice(base);
        return p;
    }

    private static PriceAdjustment adj(Product p, AdjustmentType type, AdjustmentMode mode, BigDecimal value) {
        PriceAdjustment a = new PriceAdjustment();
        a.setProduct(p);
        a.setType(type);
        a.setMode(mode);
        a.setValue(value);
        return a;
    }

    @Test
    void baseOnly_noAdjustments_returnsBasePrice() {
        Product p = product(1L, new BigDecimal("100.00"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(priceAdjustmentRepository.findByProduct(any())).thenReturn(List.of());

        PriceDto dto = priceService.getPriceNoCache(1L);

        assertThat(dto.basePrice()).isEqualByComparingTo("100.00");
        assertThat(dto.finalPrice()).isEqualByComparingTo("100.00");
        assertThat(dto.adjustments()).isEmpty();
    }

    @Test
    void absoluteAndPercent_mixed_applyAndRound() {
        Product p = product(2L, new BigDecimal("100.00"));
        when(productRepository.findById(2L)).thenReturn(Optional.of(p));
        when(priceAdjustmentRepository.findByProduct(p)).thenReturn(List.of(
                adj(p, AdjustmentType.PROMO, AdjustmentMode.ABSOLUTE, new BigDecimal("10.00")),
                adj(p, AdjustmentType.TAX, AdjustmentMode.PERCENT, new BigDecimal("2.555"))
        ));

        PriceDto dto = priceService.getPriceNoCache(2L);

        // base 100 + absolute 10 + percent(2.555%) of base(100) -> 2.56 = 112.56
        assertThat(dto.finalPrice()).isEqualByComparingTo("112.56");
    }

    @Test
    void percentOnly_appliesOnBasePrice() {
        Product p = product(3L, new BigDecimal("200.00"));
        when(productRepository.findById(3L)).thenReturn(Optional.of(p));
        when(priceAdjustmentRepository.findByProduct(p)).thenReturn(List.of(
                adj(p, AdjustmentType.FEE, AdjustmentMode.PERCENT, new BigDecimal("10.00"))
        ));

        PriceDto dto = priceService.getPriceNoCache(3L);

        assertThat(dto.finalPrice()).isEqualByComparingTo("220.00");
    }

    @Test
    void missingProduct_throwsNotFound() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> priceService.getPriceNoCache(404L))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
