package com.stopforfuel.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
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
    void getAllProducts_returnsList() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(testProduct));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Diesel"))
                .andExpect(jsonPath("$[0].price").value(89.50));
    }

    @Test
    void getActiveProducts_returnsOnlyActive() throws Exception {
        when(productService.getActiveProducts()).thenReturn(List.of(testProduct));

        mockMvc.perform(get("/api/products/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void getProductsByCategory_returnsFiltered() throws Exception {
        when(productService.getProductsByCategory("FUEL")).thenReturn(List.of(testProduct));

        mockMvc.perform(get("/api/products/category/FUEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("FUEL"));
    }

    @Test
    void getProductById_returnsProduct() throws Exception {
        when(productService.getProductById(1L)).thenReturn(testProduct);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Diesel"))
                .andExpect(jsonPath("$.hsnCode").value("27101930"));
    }

    @Test
    void getProductById_notFound_returns500() throws Exception {
        when(productService.getProductById(99L))
                .thenThrow(new RuntimeException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void createProduct_returnsCreated() throws Exception {
        when(productService.createProduct(any(Product.class))).thenReturn(testProduct);

        String json = objectMapper.writeValueAsString(testProduct);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Diesel"));
    }

    @Test
    void updateProduct_returnsUpdated() throws Exception {
        testProduct.setName("Petrol");
        when(productService.updateProduct(eq(1L), any(Product.class))).thenReturn(testProduct);

        String json = objectMapper.writeValueAsString(testProduct);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Petrol"));
    }

    @Test
    void toggleStatus_returnsToggled() throws Exception {
        testProduct.setActive(false);
        when(productService.toggleStatus(1L)).thenReturn(testProduct);

        mockMvc.perform(patch("/api/products/1/toggle-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteProduct_returnsOk() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isOk());

        verify(productService).deleteProduct(1L);
    }
}
