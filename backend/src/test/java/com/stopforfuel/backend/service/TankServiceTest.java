package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.backend.repository.TankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TankServiceTest {

    @Mock
    private TankRepository tankRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private TankService tankService;

    private Tank testTank;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Diesel");

        testTank = new Tank();
        testTank.setId(1L);
        testTank.setName("Tank A");
        testTank.setCapacity(10000.0);
        testTank.setProduct(testProduct);
        testTank.setActive(true);
        testTank.setScid(1L);
    }

    @Test
    void getAllTanks_returnsList() {
        when(tankRepository.findAll()).thenReturn(List.of(testTank));
        List<Tank> result = tankService.getAllTanks();
        assertEquals(1, result.size());
    }

    @Test
    void getActiveTanks_returnsOnlyActive() {
        when(tankRepository.findByActive(true)).thenReturn(List.of(testTank));
        List<Tank> result = tankService.getActiveTanks();
        assertEquals(1, result.size());
        assertTrue(result.get(0).isActive());
    }

    @Test
    void getTanksByProduct_returnsList() {
        when(tankRepository.findByProductId(1L)).thenReturn(List.of(testTank));
        List<Tank> result = tankService.getTanksByProduct(1L);
        assertEquals(1, result.size());
    }

    @Test
    void getTankById_exists_returnsTank() {
        when(tankRepository.findById(1L)).thenReturn(Optional.of(testTank));
        Tank result = tankService.getTankById(1L);
        assertEquals("Tank A", result.getName());
    }

    @Test
    void getTankById_notExists_throwsException() {
        when(tankRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> tankService.getTankById(99L));
    }

    @Test
    void createTank_withProduct_setsProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(tankRepository.save(any(Tank.class))).thenAnswer(i -> i.getArgument(0));

        Tank result = tankService.createTank(testTank);
        assertEquals(testProduct, result.getProduct());
    }

    @Test
    void createTank_withoutScid_defaultsTo1() {
        Tank tank = new Tank();
        tank.setName("New Tank");
        when(tankRepository.save(any(Tank.class))).thenAnswer(i -> i.getArgument(0));

        Tank result = tankService.createTank(tank);
        assertEquals(1L, result.getScid());
    }

    @Test
    void createTank_productNotFound_throwsException() {
        Product missingProduct = new Product();
        missingProduct.setId(99L);
        testTank.setProduct(missingProduct);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> tankService.createTank(testTank));
    }

    @Test
    void updateTank_updatesFields() {
        Tank details = new Tank();
        details.setName("Updated Tank");
        details.setCapacity(20000.0);
        details.setProduct(testProduct);

        when(tankRepository.findById(1L)).thenReturn(Optional.of(testTank));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(tankRepository.save(any(Tank.class))).thenAnswer(i -> i.getArgument(0));

        Tank result = tankService.updateTank(1L, details);
        assertEquals("Updated Tank", result.getName());
        assertEquals(20000.0, result.getCapacity());
    }

    @Test
    void toggleStatus_activeToInactive() {
        testTank.setActive(true);
        when(tankRepository.findById(1L)).thenReturn(Optional.of(testTank));
        when(tankRepository.save(any(Tank.class))).thenAnswer(i -> i.getArgument(0));

        Tank result = tankService.toggleStatus(1L);
        assertFalse(result.isActive());
    }

    @Test
    void toggleStatus_inactiveToActive() {
        testTank.setActive(false);
        when(tankRepository.findById(1L)).thenReturn(Optional.of(testTank));
        when(tankRepository.save(any(Tank.class))).thenAnswer(i -> i.getArgument(0));

        Tank result = tankService.toggleStatus(1L);
        assertTrue(result.isActive());
    }

    @Test
    void deleteTank_callsRepository() {
        tankService.deleteTank(1L);
        verify(tankRepository).deleteById(1L);
    }
}
