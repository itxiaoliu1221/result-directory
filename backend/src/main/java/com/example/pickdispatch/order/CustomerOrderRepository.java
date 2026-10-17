package com.example.pickdispatch.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findTop50ByOrderByCreatedAtDesc();

    List<CustomerOrder> findTop50ByStatusOrderByCreatedAtDesc(OrderStatus status);
}
