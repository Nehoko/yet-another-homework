package ge.imikhailov.omno.service;

import ge.imikhailov.omno.entity.PriceAdjustment;
import ge.imikhailov.omno.entity.Product;
import ge.imikhailov.omno.enums.AdjustmentMode;
import ge.imikhailov.omno.enums.AdjustmentType;
import ge.imikhailov.omno.repoisotory.PriceAdjustmentRepository;
import ge.imikhailov.omno.repoisotory.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SeedingService {

    private final ProductRepository productRepository;
    private final PriceAdjustmentRepository priceAdjustmentRepository;

    @PersistenceContext
    private EntityManager em;

    public record SeedResult(int products, int adjustments, long tookMs) {
    }

    /**
     * Danger: intended for test environments only.
     *
     * @return number of rows present before truncation (sum of both tables)
     */
    @Transactional
    public int clearDb() {
        int before = (int) (priceAdjustmentRepository.count() + productRepository.count());
        em.createNativeQuery("TRUNCATE pricing.price_adjustment, pricing.product RESTART IDENTITY CASCADE").executeUpdate();
        return before;
    }

    @Transactional
    public SeedResult seed(int count, double adjustRate, boolean clear) {
        long start = System.currentTimeMillis();
        if (clear) {
            clearDb();
        }

        int batchSize = 1000;
        int totalProducts = 0;
        int totalAdjustments = 0;

        List<Product> productBatch = new ArrayList<>(batchSize);
        List<PriceAdjustment> adjustmentBatch = new ArrayList<>(batchSize);

        for (int i = 1; i <= count; i++) {
            productBatch.add(buildRandomProduct(i));

            if (productBatch.size() == batchSize || i == count) {
                // Persist products for this batch
                List<Product> savedProducts = productRepository.saveAll(productBatch);
                em.flush();

                // For each saved product, decide whether to add adjustments
                for (Product p : savedProducts) {
                    int created = maybeCreateAdjustmentsFor(p, adjustRate, adjustmentBatch);
                    totalAdjustments += created;
                    // Persist adjustments in chunks to constrain memory
                    if (adjustmentBatch.size() >= batchSize) {
                        priceAdjustmentRepository.saveAll(adjustmentBatch);
                        em.flush();
                        adjustmentBatch.clear();
                    }
                }

                totalProducts += savedProducts.size();
                productBatch.clear();

                // Clear persistence context to reduce memory footprint
                em.clear();
            }
        }

        // Flush any trailing adjustments
        if (!adjustmentBatch.isEmpty()) {
            priceAdjustmentRepository.saveAll(adjustmentBatch);
            em.flush();
            adjustmentBatch.clear();
        }

        long took = System.currentTimeMillis() - start;
        return new SeedResult(totalProducts, totalAdjustments, took);
    }

    private static Product buildRandomProduct(int seq) {
        Product p = new Product();
        p.setSku("SKU-" + seq);
        p.setName("Product " + seq);
        p.setBasePrice(randomMoney(5.00, 100.00));
        return p;
    }

    private static int maybeCreateAdjustmentsFor(Product product, double adjustRate, List<PriceAdjustment> out) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= adjustRate) {
            return 0;
        }
        int k = 1 + r.nextInt(3); // 1..3 adjustments
        for (int j = 0; j < k; j++) {
            PriceAdjustment adj = new PriceAdjustment();
            adj.setProduct(product);
            AdjustmentMode mode = r.nextBoolean() ? AdjustmentMode.PERCENT : AdjustmentMode.ABSOLUTE;
            adj.setMode(mode);
            adj.setType(randomType());
            adj.setValue(mode == AdjustmentMode.PERCENT ? randomPercent(-20, 20) : randomMoney(-5.00, 5.00));
            adj.setUpdatedAt(OffsetDateTime.now());
            out.add(adj);
        }
        return k;
    }

    private static AdjustmentType randomType() {
        AdjustmentType[] values = AdjustmentType.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private static BigDecimal randomMoney(double minInclusive, double maxInclusive) {
        double v = ThreadLocalRandom.current().nextDouble(minInclusive, maxInclusive);
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal randomPercent(int minInclusive, int maxInclusive) {
        int v = ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_EVEN);
    }
}
