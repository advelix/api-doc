package io.github.advelix.demo.model;

/**
 * Request body for creating an order.
 */
public class CreateOrderRequest {

    /**
     * Product name.
     */
    private String product;

    /**
     * Order quantity.
     */
    private Integer quantity;

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
