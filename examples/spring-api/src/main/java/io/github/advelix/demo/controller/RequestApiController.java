package io.github.advelix.demo.controller;

import io.github.advelix.demo.annotation.combine.RequestApi;
import io.github.advelix.demo.model.CreateOrderRequest;
import io.github.advelix.demo.model.OrderResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Endpoints declared with the generic combined annotation {@link RequestApi}; the HTTP
 * methods are given at the use site.
 *
 * @author api-doc
 */
@io.github.advelix.demo.annotation.combine.ApiController("/api/request-orders")
public class RequestApiController {

    /**
     * Replace the order, also readable (multi-value method demo).
     *
     * @param request order fields
     * @return the replaced order
     */
    @RequestApi(method = {RequestMethod.GET, RequestMethod.PUT})
    public OrderResponse replaceOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse order = new OrderResponse();
        order.setId(2L);
        order.setProduct(request.getProduct());
        order.setQuantity(request.getQuantity());
        return order;
    }

    /**
     * Archive the order (single-value method demo).
     *
     * @param request order fields
     * @return the archived order
     */
    @RequestApi(value = "/archive", method = RequestMethod.POST)
    public OrderResponse archiveOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse order = new OrderResponse();
        order.setId(3L);
        order.setProduct(request.getProduct());
        order.setQuantity(0);
        return order;
    }
}
