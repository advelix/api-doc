package io.github.advelix.demo.model;

/**
 * Order representation.
 */
public class OrderResponse {

    /**
     * Order identifier.
     */
    private Long id;

    /**
     * Product name.
     */
    private String product;

    /**
     * Order amount.
     */
    private Integer quantity;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
