package ge.imikhailov.omno.web.error;

public class ProductNotFoundException extends RuntimeException {
    private final Long productId;

    public ProductNotFoundException(Long productId) {
        super("Product not found: " + productId);
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
