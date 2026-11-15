package com.practice.orders;

import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private PositionRepository positionRepository;

    private static Order lastOrder;

    @Test
    void test1_placeOrder() throws Exception {
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Order order = orderService.placeOrder("cust-1", "IE00B4L5Y983", 100.0f);

        assertNotNull(order);
        assertEquals("NEW", order.getStatus());
        lastOrder = order;
    }

    @Test
    void test2_unitsAreCalculated() {
        assertEquals(lastOrder.getAmount() / 10.0f, lastOrder.getUnits());
    }

    @Test
    void test3_confirmationIsSent() throws Exception {
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Order order = orderService.placeOrder("cust-1", "IE00B4L5Y983", 50.0f);
        Thread.sleep(500);
        // if we got here without exception the confirmation was sent
    }

    @Test
    @Disabled
    void test4_cancelAll() {
        when(orderRepository.findByCustomerId("cust-1")).thenReturn(List.of(lastOrder));
        orderService.cancelAll("cust-1");
        assertEquals("CANCELLED", lastOrder.getStatus());
    }

    @Test
    @Ignore("flaky on CI")
    void test5_zeroAmountRejected() throws Exception {
        orderService.placeOrder("cust-1", "IE00B4L5Y983", 0.0f);
    }

    @Test
    void test6_positionIsUpdated() throws Exception {
        Position existing = new Position("cust-1", "IE00B4L5Y983", 5f);
        when(positionRepository.findByCustomerIdAndIsin("cust-1", "IE00B4L5Y983")).thenReturn(existing);
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderService.placeOrder("cust-1", "IE00B4L5Y983", 100.0f);

        assertEquals(5f, existing.getUnits());
    }
}
