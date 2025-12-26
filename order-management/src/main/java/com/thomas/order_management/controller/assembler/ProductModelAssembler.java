package com.thomas.order_management.controller.assembler;

import com.thomas.order_management.controller.ProductController;
import com.thomas.order_management.controller.ReviewController;
import com.thomas.order_management.dto.ProductDto;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ProductModelAssembler implements RepresentationModelAssembler<ProductDto, EntityModel<ProductDto>> {

    @Override
    @SuppressWarnings("null")
    public @NonNull EntityModel<ProductDto> toModel(@NonNull ProductDto product) {
        EntityModel<ProductDto> model = EntityModel.of(product);

        // 1. Self Link
        if (product.id() != null) {
            model.add(linkTo(methodOn(ProductController.class).getProductById(product.id())).withSelfRel());
        }

        // 2. Link zu den Bewertungen (Reviews)
        // Annahme: Es gibt einen ReviewController mit getByProduct
        if (product.id() != null) {
            model.add(linkTo(methodOn(ReviewController.class).getByProduct(product.id()))
                .withRel("reviews"));
        }

        return model;
    }
}