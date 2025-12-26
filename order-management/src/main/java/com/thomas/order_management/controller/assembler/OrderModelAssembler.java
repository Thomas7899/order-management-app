package com.thomas.order_management.controller.assembler;

import com.thomas.order_management.controller.CustomerController;
import com.thomas.order_management.controller.OrderController;
import com.thomas.order_management.dto.OrderDto;
import com.thomas.order_management.model.OrderStatus;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Component
public class OrderModelAssembler implements RepresentationModelAssembler<OrderDto, EntityModel<OrderDto>> {

    @Override
    @SuppressWarnings("null")
    public @NonNull EntityModel<OrderDto> toModel(@NonNull OrderDto order) {
        EntityModel<OrderDto> orderModel = EntityModel.of(order);

        // 1. Self-Link (Immer vorhanden)
        orderModel.add(linkTo(methodOn(OrderController.class).getOrderById(order.getId())).withSelfRel());

        // 2. State-Transitions (Business Logik für Links)
        // Zeige Aktionen nur an, wenn sie im aktuellen Status erlaubt sind
        if (order.getStatus() == OrderStatus.PENDING) {
            orderModel.add(linkTo(methodOn(OrderController.class).updateOrderStatus(order.getId(), OrderStatus.CANCELLED))
                    .withRel("cancel"));
            orderModel.add(linkTo(methodOn(OrderController.class).updateOrderStatus(order.getId(), OrderStatus.SHIPPED))
                    .withRel("ship"));
        } else if (order.getStatus() == OrderStatus.SHIPPED) {
            orderModel.add(linkTo(methodOn(OrderController.class).updateOrderStatus(order.getId(), OrderStatus.DELIVERED))
                    .withRel("deliver"));
        }

        // 3. Verknüpfte Ressourcen (Null-Safe!)
        if (order.getCustomer() != null && order.getCustomer().getId() != null) {
            orderModel.add(linkTo(methodOn(CustomerController.class).getCustomerById(order.getCustomer().getId()))
                    .withRel("customer"));
        }

        return orderModel;
    }
}