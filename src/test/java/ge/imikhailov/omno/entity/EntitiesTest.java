package ge.imikhailov.omno.entity;

import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.enums.AdjustmentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EntitiesTest {

    @Test
    void productGetterSetterRoundTrip() {
        Product p = new Product();
        p.setId(7L);
        p.setSku("sku-7");
        p.setName("Product 7");
        p.setBasePrice(new BigDecimal("12.34"));

        assertThat(p.getId()).isEqualTo(7L);
        assertThat(p.getSku()).isEqualTo("sku-7");
        assertThat(p.getName()).isEqualTo("Product 7");
        assertThat(p.getBasePrice()).isEqualByComparingTo("12.34");
    }

    @Test
    void priceAdjustmentGetterSetterRoundTrip() {
        Product product = new Product();
        product.setId(5L);

        PriceAdjustment adj = new PriceAdjustment();
        adj.setId(11L);
        adj.setProduct(product);
        adj.setType(AdjustmentType.FEE);
        adj.setMode(AdjustmentMode.ABSOLUTE);
        adj.setValue(new BigDecimal("3.21"));
        OffsetDateTime ts = OffsetDateTime.now();
        adj.setUpdatedAt(ts);

        assertThat(adj.getId()).isEqualTo(11L);
        assertThat(adj.getProduct()).isSameAs(product);
        assertThat(adj.getType()).isEqualTo(AdjustmentType.FEE);
        assertThat(adj.getMode()).isEqualTo(AdjustmentMode.ABSOLUTE);
        assertThat(adj.getValue()).isEqualByComparingTo("3.21");
        assertThat(adj.getUpdatedAt()).isEqualTo(ts);
    }
}
