package com.stopforfuel.backend.config;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(
            PartyRepository partyRepository,
            com.stopforfuel.backend.repository.RolesRepository rolesRepository,
            com.stopforfuel.backend.repository.GroupRepository groupRepository,
            VehicleTypeRepository vehicleTypeRepository,
            ProductRepository productRepository,
            TankRepository tankRepository,
            PumpRepository pumpRepository,
            NozzleRepository nozzleRepository,
            CompanyRepository companyRepository,
            PaymentModeRepository paymentModeRepository) {
        return args -> {
            // Seed Payment Modes
            if (paymentModeRepository.count() == 0) {
                String[] modes = {"CASH", "CHEQUE", "UPI", "NEFT", "CARD"};
                for (String mode : modes) {
                    PaymentMode pm = new PaymentMode();
                    pm.setModeName(mode);
                    paymentModeRepository.save(pm);
                }
                System.out.println("✅ Seeded 5 payment modes");
            }

            if (partyRepository.count() == 0) {
                Party local = new Party();
                local.setPartyType("Local");
                partyRepository.save(local);

                Party statement = new Party();
                statement.setPartyType("Statement");
                partyRepository.save(statement);
            }

            if (rolesRepository.count() == 0) {
                String[] roles = {"CUSTOMER", "EMPLOYEE", "DEALER", "OWNER", "ADMIN"};
                for (String roleType : roles) {
                    Roles role = new Roles();
                    role.setRoleType(roleType);
                    rolesRepository.save(role);
                }
            }

            if (groupRepository.count() == 0) {
                Group defaultGroup = new Group();
                defaultGroup.setGroupName("Default");
                groupRepository.save(defaultGroup);
            }

            // Seed Vehicle Types
            try {
                if (vehicleTypeRepository.count() == 0) {
                    String[][] vehicleTypes = {
                        {"Car", "Standard passenger car"},
                        {"Bus", "Public or private bus"},
                        {"Truck", "Heavy duty truck"},
                        {"Jeep", "Off-road vehicle"},
                        {"Bike", "Motorcycle or scooter"}
                    };
                    for (String[] vt : vehicleTypes) {
                        VehicleType vehicleType = new VehicleType();
                        vehicleType.setTypeName(vt[0]);
                        vehicleType.setDescription(vt[1]);
                        vehicleTypeRepository.save(vehicleType);
                    }
                    System.out.println("✅ Seeded 5 vehicle types");
                }
            } catch (Exception e) {
                System.out.println("⚠️ Vehicle types seeding error: " + e.getMessage());
            }

            // Seed Products (Fuel + Lubricants)
            try {
                if (productRepository.count() == 0) {
                    // Fuel Products
                    Product petrol = createProduct("Petrol", "27101211", 107.50, "FUEL", "LITERS", null, "IOCL");
                    petrol = productRepository.save(petrol);

                    Product diesel = createProduct("Diesel", "27101990", 93.20, "FUEL", "LITERS", null, "IOCL");
                    diesel = productRepository.save(diesel);

                    Product xtraPremium = createProduct("Xtra Premium", "27101210", 112.00, "FUEL", "LITERS", null, "IOCL");
                    xtraPremium = productRepository.save(xtraPremium);

                    System.out.println("✅ Seeded 3 fuel products");

                    // Seed Tanks (only if products were just created)
                    if (tankRepository.count() == 0) {
                        Tank tank1 = createTank("Tank-1", 12000.0, petrol);
                        tank1 = tankRepository.save(tank1);

                        Tank tank2 = createTank("Tank-2", 6000.0, xtraPremium);
                        tank2 = tankRepository.save(tank2);

                        Tank tank3 = createTank("Tank-3", 10000.0, diesel);
                        tank3 = tankRepository.save(tank3);

                        System.out.println("✅ Seeded 3 tanks");

                        // Seed Pumps
                        if (pumpRepository.count() == 0) {
                            Pump pump1 = createPump("Pump-1");
                            pump1 = pumpRepository.save(pump1);

                            Pump pump2 = createPump("Pump-2");
                            pump2 = pumpRepository.save(pump2);

                            Pump pump3 = createPump("Pump-3");
                            pump3 = pumpRepository.save(pump3);

                            System.out.println("✅ Seeded 3 pumps");

                            // Seed Nozzles — 12 nozzles as per station layout
                            if (nozzleRepository.count() == 0) {
                                // Pump-1: N-1, N-2 (Petrol/Tank-1), N-7, N-8 (XP/Tank-2)
                                nozzleRepository.save(createNozzle("N-1", tank1, pump1));
                                nozzleRepository.save(createNozzle("N-2", tank1, pump1));
                                nozzleRepository.save(createNozzle("N-7", tank2, pump1));
                                nozzleRepository.save(createNozzle("N-8", tank2, pump1));

                                // Pump-2: N-3, N-4 (Petrol/Tank-1), N-9, N-10 (Diesel/Tank-3)
                                nozzleRepository.save(createNozzle("N-3", tank1, pump2));
                                nozzleRepository.save(createNozzle("N-4", tank1, pump2));
                                nozzleRepository.save(createNozzle("N-9", tank3, pump2));
                                nozzleRepository.save(createNozzle("N-10", tank3, pump2));

                                // Pump-3: N-5, N-6 (Petrol/Tank-1), N-11, N-12 (Diesel/Tank-3)
                                nozzleRepository.save(createNozzle("N-5", tank1, pump3));
                                nozzleRepository.save(createNozzle("N-6", tank1, pump3));
                                nozzleRepository.save(createNozzle("N-11", tank3, pump3));
                                nozzleRepository.save(createNozzle("N-12", tank3, pump3));

                                System.out.println("✅ Seeded 12 nozzles");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠️ Station infrastructure seeding error: " + e.getMessage());
                e.printStackTrace();
            }

            // Seed Company Details
            try {
                if (companyRepository.count() == 0) {
                    Company company = new Company();
                    company.setScid(1L);
                    company.setName("StopForFuel Station #1");
                    company.setOpenDate(java.time.LocalDate.now());
                    company.setSapCode("123456");
                    company.setGstNo("29AAAAA0000A1Z5");
                    company.setSite("Main Highway");
                    company.setType("Dealer Owned");
                    company.setAddress("123 Fuel Road, Bangalore, Karnataka");
                    companyRepository.save(company);
                    System.out.println("✅ Seeded Company details");
                }
            } catch (Exception e) {
                System.out.println("⚠️ Company seeding error: " + e.getMessage());
            }
        };
    }

    private Product createProduct(String name, String hsnCode, double price, String category, String unit, Double volume, String brand) {
        Product product = new Product();
        product.setScid(1L);
        product.setName(name);
        product.setHsnCode(hsnCode);
        product.setPrice(java.math.BigDecimal.valueOf(price));
        product.setCategory(category);
        product.setUnit(unit);
        product.setVolume(volume);
        product.setBrand(brand);
        product.setActive(true);
        return product;
    }

    private Tank createTank(String name, Double capacity, Product product) {
        Tank tank = new Tank();
        tank.setScid(1L);
        tank.setName(name);
        tank.setCapacity(capacity);
        tank.setProduct(product);
        tank.setActive(true);
        return tank;
    }

    private Pump createPump(String name) {
        Pump pump = new Pump();
        pump.setScid(1L);
        pump.setName(name);
        pump.setActive(true);
        return pump;
    }

    private Nozzle createNozzle(String nozzleName, Tank tank, Pump pump) {
        Nozzle nozzle = new Nozzle();
        nozzle.setScid(1L);
        nozzle.setNozzleName(nozzleName);
        nozzle.setTank(tank);
        nozzle.setPump(pump);
        nozzle.setActive(true);
        return nozzle;
    }
}
