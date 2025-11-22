package ge.imikhailov.omno.repoisotory;

import ge.imikhailov.omno.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
