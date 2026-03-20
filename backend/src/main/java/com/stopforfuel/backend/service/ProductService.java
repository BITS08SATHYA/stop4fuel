package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.repository.GradeTypeRepository;
import com.stopforfuel.backend.repository.ProductRepository;
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
        return productRepository.findAll();
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByActive(true);
    }

    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategoryIgnoreCaseAndActive(category, true);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
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
        if (product.getGrade() != null && product.getGrade().getId() != null && product.getOilType() != null) {
            GradeType grade = gradeTypeRepository.findById(product.getGrade().getId())
                    .orElseThrow(() -> new RuntimeException("Grade not found with id: " + product.getGrade().getId()));
            if (grade.getOilType() != null
                    && !grade.getOilType().getId().equals(product.getOilType().getId())) {
                throw new RuntimeException(
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
        productRepository.deleteById(id);
    }
}
