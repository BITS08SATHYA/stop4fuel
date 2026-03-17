package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Diesel");
        testProduct.setHsnCode("27101930");
        testProduct.setPrice(new BigDecimal("89.50"));
        testProduct.setCategory("FUEL");
        testProduct.setUnit("LITERS");
        testProduct.setActive(true);
    }

    @Test
    void getAllProducts_returnsList() {
        when(productRepository.findAll()).thenReturn(List.of(testProduct));

        List<Product> result = productService.getAllProducts();

        assertEquals(1, result.size());
        assertEquals("Diesel", result.get(0).getName());
    }

    @Test
    void getActiveProducts_returnsOnlyActive() {
        when(productRepository.findByActive(true)).thenReturn(List.of(testProduct));

        List<Product> result = productService.getActiveProducts();

        assertEquals(1, result.size());
        assertTrue(result.get(0).isActive());
    }

    @Test
    void getProductsByCategory_filtersCorrectly() {
        when(productRepository.findByCategoryIgnoreCaseAndActive("FUEL", true))
                .thenReturn(List.of(testProduct));

        List<Product> result = productService.getProductsByCategory("FUEL");

        assertEquals(1, result.size());
        assertEquals("FUEL", result.get(0).getCategory());
    }

    @Test
    void getProductById_exists_returnsProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        Product result = productService.getProductById(1L);

        assertEquals("Diesel", result.getName());
    }

    @Test
    void getProductById_notExists_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> productService.getProductById(99L));
        assertTrue(ex.getMessage().contains("Product not found"));
    }

    @Test
    void createProduct_savesAndReturns() {
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        Product result = productService.createProduct(testProduct);

        assertEquals("Diesel", result.getName());
        verify(productRepository).save(testProduct);
    }

    @Test
    void updateProduct_updatesAllFields() {
        Product updated = new Product();
        updated.setName("Petrol");
        updated.setHsnCode("27101910");
        updated.setPrice(new BigDecimal("95.00"));
        updated.setCategory("FUEL");
        updated.setUnit("LITERS");
        updated.setVolume(null);
        updated.setBrand("IOCL");

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        Product result = productService.updateProduct(1L, updated);

        assertEquals("Petrol", result.getName());
        assertEquals("27101910", result.getHsnCode());
        assertEquals(new BigDecimal("95.00"), result.getPrice());
        assertEquals("IOCL", result.getBrand());
    }

    @Test
    void toggleStatus_activeToInactive() {
        testProduct.setActive(true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        Product result = productService.toggleStatus(1L);

        assertFalse(result.isActive());
    }

    @Test
    void toggleStatus_inactiveToActive() {
        testProduct.setActive(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        Product result = productService.toggleStatus(1L);

        assertTrue(result.isActive());
    }

    @Test
    void deleteProduct_callsDeleteById() {
        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }
}
