package ge.imikhailov.omno.repoisotory;

import ge.imikhailov.omno.entity.PriceAdjustment;
import ge.imikhailov.omno.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceAdjustmentRepository extends JpaRepository<PriceAdjustment, Long> {
    List<PriceAdjustment> findByProduct(Product productid);
}
