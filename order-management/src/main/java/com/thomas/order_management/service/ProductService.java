package com.thomas.order_management.service;

import com.thomas.order_management.dto.ProductDto;
import com.thomas.order_management.dto.ProductRequest;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.mapper.ProductMapper;
import com.thomas.order_management.model.ChangeDocument;
import com.thomas.order_management.model.NumberRangeObject;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.TaxCode;
import com.thomas.order_management.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pflege des Materialstamms (angelehnt an MM01/MM02).
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final NumberRangeService numberRangeService;
    private final ChangeDocumentService changeDocuments;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper,
                          NumberRangeService numberRangeService, ChangeDocumentService changeDocuments) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.numberRangeService = numberRangeService;
        this.changeDocuments = changeDocuments;
    }

    public ProductDto create(ProductRequest request) {
        Product product = new Product(request.name(), request.description(), request.price(), 0);
        product.setMaterialNumber(numberRangeService.next(NumberRangeObject.MATERIAL));
        apply(product, request);
        Product saved = productRepository.save(product);
        changeDocuments.logEvent(ChangeDocument.ObjectClass.MATERIAL, saved.getId(), saved.getMaterialNumber(),
                ChangeDocument.ChangeType.INSERT, "Material angelegt");
        return productMapper.toDto(saved);
    }

    public ProductDto update(Long id, ProductRequest request) {
        Product product = load(id);
        ChangeDocument.ObjectClass oc = ChangeDocument.ObjectClass.MATERIAL;
        String number = product.getMaterialNumber();
        changeDocuments.logFieldChange(oc, id, number, "Bezeichnung", product.getName(), request.name());
        changeDocuments.logFieldChange(oc, id, number, "Verkaufspreis", product.getPrice(), request.price());
        changeDocuments.logFieldChange(oc, id, number, "Einstandspreis", product.getCostPrice(), request.costPrice());
        changeDocuments.logFieldChange(oc, id, number, "Warengruppe", product.getCategory(), request.category());
        if (request.taxCode() != null) {
            changeDocuments.logFieldChange(oc, id, number, "Steuerkennzeichen", product.getTaxCode(), request.taxCode());
        }
        if (request.active() != null) {
            changeDocuments.logFieldChange(oc, id, number, "Vertriebssperre", !product.getActive(), !request.active());
        }
        apply(product, request);
        return productMapper.toDto(productRepository.save(product));
    }

    /** Setzt die Vertriebssperre; Materialien mit Belegen werden in SAP nicht gelöscht. */
    public void block(Long id) {
        Product product = load(id);
        if (Boolean.TRUE.equals(product.getActive())) {
            changeDocuments.logFieldChange(ChangeDocument.ObjectClass.MATERIAL, id, product.getMaterialNumber(),
                    "Vertriebssperre", false, true);
            product.setActive(false);
            productRepository.save(product);
        }
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCostPrice(request.costPrice());
        product.setCategory(request.category());
        product.setUnitOfMeasure(request.unitOfMeasure() != null ? request.unitOfMeasure() : "ST");
        product.setTaxCode(request.taxCode() != null ? request.taxCode() : TaxCode.FULL);
        product.setImageUrl(request.imageUrl());
        if (request.active() != null) {
            product.setActive(request.active());
        }
    }

    private Product load(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material " + id + " nicht gefunden"));
    }
}
