package com.thomas.order_management.mapper;

import com.thomas.order_management.dto.ProductDto;
import com.thomas.order_management.model.Product;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductDto toDto(Product product);
    List<ProductDto> toDtoList(List<Product> products);
    default Page<ProductDto> toDtoPage(Page<Product> page) {
        return page.map(this::toDto);
    }
}