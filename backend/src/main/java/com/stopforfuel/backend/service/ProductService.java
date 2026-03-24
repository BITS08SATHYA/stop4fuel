package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.GradeTypeRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private GradeTypeRepository gradeTypeRepository;

    public List<Product> getAllProducts() {
        return productRepository.findAllByScid(SecurityUtils.getScid());
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByActiveAndScid(true, SecurityUtils.getScid());
    }

    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategoryIgnoreCaseAndActiveAndScid(category, true, SecurityUtils.getScid());
    }

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
        product.setName(productDetails.getName());
        product.setHsnCode(productDetails.getHsnCode());
        product.setPrice(productDetails.getPrice());
        product.setCategory(productDetails.getCategory());
        product.setUnit(productDetails.getUnit());
        product.setVolume(productDetails.getVolume());
        product.setBrand(productDetails.getBrand());
        product.setSupplier(productDetails.getSupplier());
        product.setOilType(productDetails.getOilType());
        product.setGrade(productDetails.getGrade());
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
