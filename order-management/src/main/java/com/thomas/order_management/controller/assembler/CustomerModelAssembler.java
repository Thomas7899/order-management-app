package com.thomas.order_management.controller.assembler;

import com.thomas.order_management.controller.CustomerController;
import com.thomas.order_management.controller.OrderController;
import com.thomas.order_management.dto.CustomerDTO;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class CustomerModelAssembler implements RepresentationModelAssembler<CustomerDTO, EntityModel<CustomerDTO>> {

    @Override
    @SuppressWarnings("null")
    public @NonNull EntityModel<CustomerDTO> toModel(@NonNull CustomerDTO customer) {
        EntityModel<CustomerDTO> model = EntityModel.of(customer);

        // 1. Self Link
        model.add(linkTo(methodOn(CustomerController.class).getCustomerById(customer.getId())).withSelfRel());

        // 2. Link zu den Bestellungen dieses Kunden (Cross-Controller Link!)
        // Das ist echtes HATEOAS: Vom Kunden direkt zu seinen Bestellungen navigieren.
        model.add(linkTo(methodOn(OrderController.class).getOrdersByCustomer(customer.getId()))
                .withRel("orders"));

        return model;
    }
}