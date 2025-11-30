package ge.imikhailov.omno.service;

import ge.imikhailov.omno.entity.PriceAdjustment;
import ge.imikhailov.omno.entity.Product;
import ge.imikhailov.omno.repoisotory.PriceAdjustmentRepository;
import ge.imikhailov.omno.repoisotory.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeedingServiceTest {

    private ProductRepository productRepository;
    private PriceAdjustmentRepository priceAdjustmentRepository;
    private EntityManager em;
    private SeedingService seedingService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        priceAdjustmentRepository = mock(PriceAdjustmentRepository.class);
        em = mock(EntityManager.class);
        seedingService = new SeedingService(productRepository, priceAdjustmentRepository);
        // inject mock EntityManager via reflection since field is package-private
        try {
            var field = SeedingService.class.getDeclaredField("em");
            field.setAccessible(true);
            field.set(seedingService, em);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void clearDbTruncatesTables() {
        when(priceAdjustmentRepository.count()).thenReturn(3L);
        when(productRepository.count()).thenReturn(5L);
        var query = mock(jakarta.persistence.Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);

        int truncated = seedingService.clearDb();

        assertThat(truncated).isEqualTo(8);
        verify(em).createNativeQuery("TRUNCATE pricing.price_adjustment, pricing.product RESTART IDENTITY CASCADE");
        verify(query).executeUpdate();
    }

    @Test
    void seedCreatesProductsAndOptionalAdjustments() {
        // Using adjustRate=1.0 guarantees adjustments will be attempted
        when(productRepository.count()).thenReturn(0L);
        when(productRepository.saveAll(anyList())).thenAnswer(inv -> new ArrayList<>((List<Product>) inv.getArgument(0)));
        when(priceAdjustmentRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SeedingService.SeedResult result = seedingService.seed(2, 1.0, false);

        assertThat(result.products()).isEqualTo(2);
        assertThat(result.adjustments()).isGreaterThanOrEqualTo(2); // each product gets at least one
        assertThat(result.tookMs()).isGreaterThanOrEqualTo(0);

        verify(productRepository, atLeastOnce()).saveAll(anyList());
        verify(priceAdjustmentRepository, atLeastOnce()).saveAll(anyList());
        verify(em, atLeastOnce()).flush();
        verify(em, atLeastOnce()).clear();
    }
}
