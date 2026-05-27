package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.event.FuelPriceChangedEvent;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.GradeTypeRepository;
import com.stopforfuel.backend.repository.InvoiceProductRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    private final GradeTypeRepository gradeTypeRepository;

    private final InvoiceProductRepository invoiceProductRepository;

    private final ProductPriceHistoryService productPriceHistoryService;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<Product> getActiveProducts() {
        return productRepository.findByActiveAndScid(true, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategoryIgnoreCaseAndActiveAndScid(category, true, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<Product> getActiveNonFuelProducts() {
        return productRepository.findByCategoryNotIgnoreCaseAndActiveAndScid("FUEL", true, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<Product> getTopSellingProducts(int days, int limit) {
        Long scid = SecurityUtils.getScid();
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(days, 1));
        List<Long> ids = invoiceProductRepository.findTopSellingProductIds(
                scid, since, PageRequest.of(0, Math.max(limit, 1)));
        if (ids.isEmpty()) return List.of();
        List<Product> products = productRepository.findAllById(ids).stream()
                .filter(p -> p.isActive() && scid.equals(p.getScid()))
                .toList();
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);
        return products.stream()
                .sorted(Comparator.comparingInt(p -> order.getOrDefault(p.getId(), Integer.MAX_VALUE)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public Product createProduct(Product product) {
        validateGradeOilTypeLink(product);
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Product productDetails) {
        validateGradeOilTypeLink(productDetails);
        Product product = getProductById(id);
        BigDecimal previousPrice = product.getPrice();
        product.setName(productDetails.getName());
        product.setHsnCode(productDetails.getHsnCode());
        product.setPrice(productDetails.getPrice());
        if (productDetails.getPrice() != null
                && (previousPrice == null || previousPrice.compareTo(productDetails.getPrice()) != 0)) {
            productPriceHistoryService.recordPriceChange(product, previousPrice, productDetails.getPrice());
            if ("FUEL".equalsIgnoreCase(product.getCategory())) {
                eventPublisher.publishEvent(new FuelPriceChangedEvent(
                        product.getScid(), product.getId(), previousPrice, productDetails.getPrice()));
            }
        }
        product.setCategory(productDetails.getCategory());
        product.setUnit(productDetails.getUnit());
        product.setVolume(productDetails.getVolume());
        product.setBrand(productDetails.getBrand());
        product.setSupplier(productDetails.getSupplier());
        product.setOilType(productDetails.getOilType());
        product.setGrade(productDetails.getGrade());
        product.setDiscountRate(productDetails.getDiscountRate());
        return productRepository.save(product);
    }

    private void validateGradeOilTypeLink(Product product) {
        if (product.getGrade() != null && product.getGrade().getId() != null) {
            GradeType grade = gradeTypeRepository.findById(product.getGrade().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Grade not found with id: " + product.getGrade().getId()));
            if (!grade.isActive()) {
                throw new BusinessException(
                        "Grade '" + grade.getName() + "' is disabled and cannot be used for new products");
            }
            if (product.getOilType() != null && grade.getOilType() != null
                    && !grade.getOilType().getId().equals(product.getOilType().getId())) {
                throw new BusinessException(
                        "Grade '" + grade.getName() + "' belongs to oil type '" +
                        grade.getOilType().getName() + "', not the selected oil type");
            }
        }
    }

    @Transactional
    public Product updatePrice(Long id, BigDecimal newPrice) {
        Product product = getProductById(id);
        BigDecimal previousPrice = product.getPrice();
        product.setPrice(newPrice);
        Product saved = productRepository.save(product);
        if (newPrice != null && (previousPrice == null || previousPrice.compareTo(newPrice) != 0)) {
            productPriceHistoryService.recordPriceChange(saved, previousPrice, newPrice);
            if ("FUEL".equalsIgnoreCase(saved.getCategory())) {
                eventPublisher.publishEvent(new FuelPriceChangedEvent(
                        saved.getScid(), saved.getId(), previousPrice, newPrice));
            }
        }
        return saved;
    }

    public Product toggleStatus(Long id) {
        Product product = getProductById(id);
        product.setActive(!product.isActive());
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        productRepository.delete(product);
    }
}
