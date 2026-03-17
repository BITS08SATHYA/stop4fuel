package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private GroupService groupService;

    private Group testGroup;

    @BeforeEach
    void setUp() {
        testGroup = new Group();
        testGroup.setId(1L);
        testGroup.setGroupName("Fleet A");
        testGroup.setDescription("Primary fleet");
    }

    @Test
    void getAllGroups_returnsList() {
        when(groupRepository.findAll()).thenReturn(List.of(testGroup));
        assertEquals(1, groupService.getAllGroups().size());
    }

    @Test
    void createGroup_savesAndReturns() {
        when(groupRepository.save(testGroup)).thenReturn(testGroup);
        assertEquals("Fleet A", groupService.createGroup(testGroup).getGroupName());
    }

    @Test
    void updateGroup_updatesFields() {
        Group details = new Group();
        details.setGroupName("Updated Fleet");
        details.setDescription("Updated desc");

        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(groupRepository.save(any(Group.class))).thenAnswer(i -> i.getArgument(0));

        Group result = groupService.updateGroup(1L, details);
        assertEquals("Updated Fleet", result.getGroupName());
        assertEquals("Updated desc", result.getDescription());
    }

    @Test
    void updateGroup_notFound_throwsException() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> groupService.updateGroup(99L, new Group()));
    }

    @Test
    void deleteGroup_unlinksCustomersAndDeletes() {
        Customer c1 = new Customer();
        c1.setGroup(testGroup);
        Customer c2 = new Customer();
        c2.setGroup(testGroup);
        testGroup.setCustomers(new ArrayList<>(List.of(c1, c2)));

        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));

        groupService.deleteGroup(1L);

        assertNull(c1.getGroup());
        assertNull(c2.getGroup());
        verify(customerRepository).saveAll(anyList());
        verify(groupRepository).delete(testGroup);
    }

    @Test
    void deleteGroup_notFound_throwsException() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> groupService.deleteGroup(99L));
    }
}
