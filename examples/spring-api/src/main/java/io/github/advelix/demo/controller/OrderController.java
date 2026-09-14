package io.github.advelix.demo.controller;

import io.github.advelix.demo.annotation.combine.ApiController;
import io.github.advelix.demo.annotation.combine.GetApi;
import io.github.advelix.demo.annotation.combine.PostApi;
import io.github.advelix.demo.model.CreateOrderRequest;
import io.github.advelix.demo.model.OrderResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Order endpoints, declared with the combined custom annotations.
 *
 * @author api-doc
 */
@ApiController("/api/orders")
public class OrderController {

    /**
     * Get a single order by id.
     *
     * @param id order id
     * @return the order
     */
    @GetApi("/{id}")
    public OrderResponse getOrder(@PathVariable("id") Long id) {
        OrderResponse order = new OrderResponse();
        order.setId(id);
        order.setProduct("demo-product");
        order.setQuantity(1);
        return order;
    }

    /**
     * Create a new order.
     *
     * @param request order fields
     * @return the created order
     */
    @PostApi
    public OrderResponse createOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse order = new OrderResponse();
        order.setId(1L);
        order.setProduct(request.getProduct());
        order.setQuantity(request.getQuantity());
        return order;
    }
}
